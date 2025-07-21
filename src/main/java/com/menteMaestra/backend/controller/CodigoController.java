package com.menteMaestra.backend.controller;

import com.menteMaestra.backend.model.Pregunta;
import com.menteMaestra.backend.model.Opcion;
import com.menteMaestra.backend.service.PreguntaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controlador REST que gestiona la lógica de las partidas de preguntas y respuestas.
 * Maneja la creación de códigos de partida, la unión de jugadores, el inicio del juego,
 * el flujo de preguntas, la recepción de respuestas y el cálculo de puntajes.
 * Utiliza WebSockets para notificar a los clientes en tiempo real sobre el estado del juego.
 *
 * @apiNote Toda la lógica de estado de la partida se maneja en memoria. Esto significa que
 * si la aplicación se reinicia, todas las partidas en curso se perderán.
 */
@RestController
@CrossOrigin(origins = "http://localhost:3000")
@RequestMapping("/api/codigo")
public class CodigoController {

    private final Set<String> codigosActivos = new HashSet<>();
    private final Map<String, List<String>> jugadoresPorCodigo = new HashMap<>();
    private final Map<String, List<Pregunta>> preguntasPorCodigo = new HashMap<>();
    private final Map<String, Map<String, String>> infoJugadoresPorCodigo = new HashMap<>();
    private final Set<String> partidasIniciadas = new HashSet<>();
    private final Map<String, Integer> preguntaActualPorCodigo = new HashMap<>();
    private final Map<String, Map<String, Integer>> puntajesPorCodigo = new HashMap<>();
    private final Map<String, Long> tiempoInicioPreguntaPorCodigo = new HashMap<>();
    private static final int DURACION_PREGUNTA_SEGUNDOS = 15;
    private final Map<String, Set<String>> respuestasRecibidasPorCodigo = new HashMap<>();
    private final Map<String, Integer> cantidadPreguntasPorCodigo = new HashMap<>();
    private final SimpMessagingTemplate messagingTemplate;
    private final PreguntaService preguntaService;
    private static final String ERROR_KEY = "error";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CODIGO_KEY = "codigo";
    private static final String NOMBRE_KEY = "nombre";
    private static final String CODIGO_NO_ENCONTRADO = "Código no encontrado";

    @Autowired
    public CodigoController(PreguntaService preguntaService, SimpMessagingTemplate messagingTemplate) {
        this.preguntaService = preguntaService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Endpoint para generar un nuevo código de partida de 6 dígitos.
     * @apiNote Inicializa las estructuras de datos en memoria para la nueva partida.
     * @return Un ResponseEntity con un mapa que contiene el código generado o un error 500.
     */
    @PostMapping("/generar")
    public ResponseEntity<Map<String, String>> generarCodigo() {
        try {
            String codigo = String.format("%06d", RANDOM.nextInt(999999));
            codigosActivos.add(codigo);
            jugadoresPorCodigo.put(codigo, new ArrayList<>());
            infoJugadoresPorCodigo.put(codigo, new HashMap<>());
            return ResponseEntity.ok().body(Map.of(CODIGO_KEY, codigo));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(ERROR_KEY, "Error al generar código"));
        }
    }

    /**
     * Endpoint para que un jugador o administrador se una a una partida existente.
     * @param body Un mapa que debe contener "codigo" y "nombre". Opcionalmente, "rol" y "cantidadPreguntas" si es admin.
     * @return Un ResponseEntity con un mensaje de bienvenida o un error si los datos son inválidos,
     * el código no existe, la partida ya comenzó o el nombre de usuario ya está en uso.
     */
    @PostMapping("/ingresar")
    public ResponseEntity<Map<String, String>> ingresarPartida(@RequestBody Map<String, String> body) {
        String codigo = body.get(CODIGO_KEY);
        String nombre = body.get(NOMBRE_KEY);
        String rol = body.getOrDefault("rol", "jugador");

        if (codigo == null || codigo.length() != 6 || nombre == null || nombre.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Datos inválidos"));
        }

        if (!codigosActivos.contains(codigo)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(ERROR_KEY, CODIGO_NO_ENCONTRADO));
        }

        if (partidasIniciadas.contains(codigo)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(ERROR_KEY, "La partida ya ha sido iniciada"));
        }

        List<String> jugadores = jugadoresPorCodigo.get(codigo);

        if (jugadores.size() >= 5) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(ERROR_KEY, "Ooops, ya hay muchos jugadores en esta partida"));
        }

        if (jugadores.contains(nombre)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(ERROR_KEY, "Ese nombre ya está en uso en esta partida"));
        }

        jugadores.add(nombre);
        infoJugadoresPorCodigo.computeIfAbsent(codigo, k -> new HashMap<>()).put(nombre, rol);
        puntajesPorCodigo.computeIfAbsent(codigo, k -> new HashMap<>()).put(nombre, 0);

        if ("admin".equals(rol)) {
            String cantidadStr = body.getOrDefault("cantidadPreguntas", "5");
            try {
                int cantidad = Integer.parseInt(cantidadStr);
                cantidadPreguntasPorCodigo.put(codigo, cantidad);
            } catch (NumberFormatException e) {
                return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Cantidad de preguntas inválida"));
            }
        }

        return ResponseEntity.ok(Map.of("message", "Bienvenido"));
    }

    /**
     * Endpoint para obtener la lista de jugadores y sus roles en una partida específica.
     * @param codigo El código de la partida.
     * @return Un ResponseEntity con un mapa de {nombre: rol} o un error 404 si el código no existe.
     */
    @GetMapping("/jugadores/{codigo}")
    public ResponseEntity<Object>  obtenerJugadores(@PathVariable String codigo) {
        if (!codigosActivos.contains(codigo)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CODIGO_NO_ENCONTRADO);
        }

        return ResponseEntity.ok(infoJugadoresPorCodigo.getOrDefault(codigo, new HashMap<>()));
    }

    /**
     * Endpoint para que un jugador abandone una partida.
     * @param body Un mapa que debe contener "codigo" y "nombre" del jugador que sale.
     * @return Un ResponseEntity 200 OK si la operación fue exitosa o 404 si el código no existe.
     */
    @DeleteMapping("/salir")
    public ResponseEntity<Object> salirDePartida(@RequestBody Map<String, String> body) {
        String codigo = body.get(CODIGO_KEY);
        String nombre = body.get(NOMBRE_KEY);

        if (!codigosActivos.contains(codigo)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CODIGO_NO_ENCONTRADO);
        }

        jugadoresPorCodigo.getOrDefault(codigo, new ArrayList<>()).remove(nombre);
        infoJugadoresPorCodigo.getOrDefault(codigo, new HashMap<>()).remove(nombre);
        puntajesPorCodigo.getOrDefault(codigo, new HashMap<>()).remove(nombre);
        respuestasRecibidasPorCodigo.getOrDefault(codigo, new HashSet<>()).remove(nombre);

        return ResponseEntity.ok().build();
    }

    /**
     * Endpoint para que el administrador inicie la partida.
     * @apiNote Esta acción es irreversible para la partida. Selecciona las preguntas y las guarda en memoria.
     * @param body Un mapa que debe contener "codigo" y "nombre" del administrador.
     * @return Un ResponseEntity con un mensaje de éxito o un error si el usuario no es admin o el código no existe.
     */
    @PostMapping("/iniciar")
    public ResponseEntity<Object> iniciarPartida(@RequestBody Map<String, String> body) {
        String codigo = body.get(CODIGO_KEY);
        String nombre = body.get(NOMBRE_KEY);

        if (!codigosActivos.contains(codigo)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CODIGO_NO_ENCONTRADO);
        }

        if (!"admin".equals(infoJugadoresPorCodigo.getOrDefault(codigo, new HashMap<>()).get(nombre))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Solo el administrador puede iniciar la partida");
        }

        int cantidad = cantidadPreguntasPorCodigo.getOrDefault(codigo, 5);
        List<Pregunta> preguntasMezcladas = preguntaService.obtenerPreguntasMezcladas();
        preguntasPorCodigo.put(codigo, preguntasMezcladas.stream().limit(cantidad).toList());

        partidasIniciadas.add(codigo);
        preguntaActualPorCodigo.put(codigo, 0);
        tiempoInicioPreguntaPorCodigo.put(codigo, System.currentTimeMillis());
        respuestasRecibidasPorCodigo.put(codigo, new HashSet<>());

        return ResponseEntity.ok(Map.of("message", "Partida iniciada"));
    }

    /**
     * Endpoint para consultar si una partida ya ha sido iniciada.
     * @param codigo El código de la partida a consultar.
     * @return Un ResponseEntity con un mapa que contiene el booleano "iniciada".
     */
    @GetMapping("/estado/{codigo}")
    public ResponseEntity<Object> estadoPartida(@PathVariable String codigo) {
        boolean iniciada = partidasIniciadas.contains(codigo);
        return ResponseEntity.ok(Map.of("iniciada", iniciada));
    }

    /**
     * Endpoint para obtener la pregunta actual de una partida.
     * @apiNote Este es un endpoint clave que también controla el avance del juego. Si el tiempo se acaba
     * o todos responden, avanza a la siguiente pregunta y notifica a los clientes vía WebSocket.
     * @param codigo El código de la partida.
     * @return Un ResponseEntity con los datos de la pregunta actual (enunciado, opciones, tiempo)
     * o un objeto indicando el fin del juego.
     */
    @GetMapping("/pregunta/{codigo}")
    public ResponseEntity<Object> obtenerPreguntaActual(@PathVariable String codigo) {
        if (!partidasIniciadas.contains(codigo)) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "La partida no ha comenzado"));
        }

        int index = preguntaActualPorCodigo.getOrDefault(codigo, 0);
        List<Pregunta> preguntas = preguntasPorCodigo.getOrDefault(codigo, new ArrayList<>());

        if (index >= preguntas.size()) {
            return ResponseEntity.ok(Map.of("fin", true));
        }

        Pregunta pregunta = preguntas.get(index);

        long inicio = tiempoInicioPreguntaPorCodigo.getOrDefault(codigo, System.currentTimeMillis());
        long ahora = System.currentTimeMillis();
        long transcurrido = (ahora - inicio) / 1000;
        int restante = Math.max(0, DURACION_PREGUNTA_SEGUNDOS - (int) transcurrido);

        boolean todosRespondieron = respuestasRecibidasPorCodigo.getOrDefault(codigo, Set.of())
                .containsAll(jugadoresPorCodigo.getOrDefault(codigo, List.of()));

        if (restante == 0 || todosRespondieron) {
            index++;
            if (index >= preguntas.size()) {
                // Finalizó la partida
                Map<String, Object> finPayload = new HashMap<>();
                finPayload.put("fin", true);
                messagingTemplate.convertAndSend("/topic/pregunta/" + codigo, (Object) finPayload);

                return ResponseEntity.ok(finPayload);
            }

            preguntaActualPorCodigo.put(codigo, index);
            tiempoInicioPreguntaPorCodigo.put(codigo, System.currentTimeMillis());
            respuestasRecibidasPorCodigo.put(codigo, new HashSet<>());

            Pregunta siguiente = preguntas.get(index);
            List<Opcion> opcionesSiguiente = new ArrayList<>(siguiente.getOpciones());
            Collections.shuffle(opcionesSiguiente);

            Map<String, Object> nuevaPreguntaPayload = Map.of(
                    "enunciado", siguiente.getEnunciado(),
                    "opciones", opcionesSiguiente,
                    "tiempoRestante", DURACION_PREGUNTA_SEGUNDOS,
                    "nuevaPregunta", true
            );
            messagingTemplate.convertAndSend("/topic/pregunta/" + codigo, (Object) nuevaPreguntaPayload);


            return ResponseEntity.ok(nuevaPreguntaPayload);
        }

        return ResponseEntity.ok(Map.of(
                "enunciado", pregunta.getEnunciado(),
                "opciones", pregunta.getOpciones(),
                "tiempoRestante", restante,
                "nuevaPregunta", false
        ));
    }

    /**
     * Endpoint para que un jugador registre su respuesta a la pregunta actual.
     * @param body Un mapa que debe contener "codigo", "nombre" y "respuesta" del jugador.
     * @return Un ResponseEntity indicando si la respuesta fue "correcta" y el "puntaje" actualizado del jugador.
     */
    @PostMapping("/respuesta")
    public ResponseEntity<Object> registrarRespuesta(@RequestBody Map<String, String> body) {
        String codigo = body.get(CODIGO_KEY);
        String nombre = body.get(NOMBRE_KEY);
        String respuestaJugador = body.get("respuesta");

        int index = preguntaActualPorCodigo.getOrDefault(codigo, 0);
        List<Pregunta> preguntas = preguntasPorCodigo.getOrDefault(codigo, new ArrayList<>());
        if (index >= preguntas.size()) {
            return ResponseEntity.badRequest().body("Índice fuera de rango");
        }
        Pregunta pregunta = preguntas.get(index);

        if (pregunta == null) {
            return ResponseEntity.badRequest().body("No hay pregunta activa para este código");
        }

        if (respuestasRecibidasPorCodigo.getOrDefault(codigo, Set.of()).contains(nombre)) {
            return ResponseEntity.badRequest().body("Ya respondiste esta pregunta");
        }

        respuestasRecibidasPorCodigo.computeIfAbsent(codigo, k -> new HashSet<>()).add(nombre);

        boolean esCorrecta = pregunta.getOpciones().stream()
                .anyMatch(op -> op.getTexto().trim().equalsIgnoreCase(respuestaJugador.trim()) && op.isCorrecta());

        if (esCorrecta) {
            puntajesPorCodigo.getOrDefault(codigo, new HashMap<>())
                    .merge(nombre, 5, Integer::sum);
        }

        return ResponseEntity.ok(Map.of(
                "correcta", esCorrecta,
                "puntaje", puntajesPorCodigo.getOrDefault(codigo, new HashMap<>()).getOrDefault(nombre, 0)
        ));
    }

    /**
     * Endpoint para obtener la tabla de puntajes de una partida.
     * @param codigo El código de la partida.
     * @return Un ResponseEntity con un mapa de {nombre: puntaje} para todos los jugadores activos.
     */
    @GetMapping("/puntajes/{codigo}")
    public ResponseEntity<Object> obtenerPuntajes(@PathVariable String codigo) {
        Map<String, Integer> puntajes = puntajesPorCodigo.getOrDefault(codigo, new HashMap<>());
        List<String> jugadoresActivos = jugadoresPorCodigo.getOrDefault(codigo, new ArrayList<>());

        Map<String, Integer> resultado = puntajes.entrySet().stream()
                .filter(e -> jugadoresActivos.contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return ResponseEntity.ok(resultado);
    }

}

