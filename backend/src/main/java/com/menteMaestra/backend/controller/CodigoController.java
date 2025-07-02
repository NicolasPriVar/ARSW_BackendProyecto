package com.menteMaestra.backend.controller;

import com.menteMaestra.backend.model.Pregunta;
import com.menteMaestra.backend.model.Opcion;
import com.menteMaestra.backend.service.PreguntaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@CrossOrigin(origins = "http://localhost:3000")
@RequestMapping("/api/codigo")
public class CodigoController {

    private final Set<String> codigosActivos = new HashSet<>();
    private final Map<String, List<String>> jugadoresPorCodigo = new HashMap<>();
    private final Map<String, Map<String, String>> infoJugadoresPorCodigo = new HashMap<>();
    private final Set<String> partidasIniciadas = new HashSet<>();
    private final Map<String, Integer> preguntaActualPorCodigo = new HashMap<>();
    private final Map<String, Map<String, Integer>> puntajesPorCodigo = new HashMap<>();
    private final Map<String, Long> tiempoInicioPreguntaPorCodigo = new HashMap<>();
    private static final int DURACION_PREGUNTA_SEGUNDOS = 15;

    @Autowired
    private PreguntaService preguntaService;

    @PostMapping("/generar")
    public ResponseEntity<Map<String, String>> generarCodigo() {
        try {
            String codigo = String.format("%06d", new Random().nextInt(999999));
            codigosActivos.add(codigo);
            jugadoresPorCodigo.put(codigo, new ArrayList<>());
            infoJugadoresPorCodigo.put(codigo, new HashMap<>());
            return ResponseEntity.ok().body(Map.of("codigo", codigo));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al generar código"));
        }
    }

    @PostMapping("/ingresar")
    public ResponseEntity<Map<String, String>> ingresarPartida(@RequestBody Map<String, String> body) {
        String codigo = body.get("codigo");
        String nombre = body.get("nombre");
        String rol = body.getOrDefault("rol", "jugador");

        if (codigo == null || codigo.length() != 6 || nombre == null || nombre.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Datos inválidos"));
        }

        if (!codigosActivos.contains(codigo)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Código no encontrado"));
        }

        if (partidasIniciadas.contains(codigo)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "La partida ya ha sido iniciada"));
        }

        List<String> jugadores = jugadoresPorCodigo.get(codigo);
        if (jugadores.contains(nombre)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Ese nombre ya está en uso en esta partida"));
        }

        jugadores.add(nombre);
        infoJugadoresPorCodigo.computeIfAbsent(codigo, k -> new HashMap<>()).put(nombre, rol);
        puntajesPorCodigo.computeIfAbsent(codigo, k -> new HashMap<>()).put(nombre, 0); // <<--- AQUÍ
        return ResponseEntity.ok(Map.of("message", "Bienvenido"));
    }

    @GetMapping("/jugadores/{codigo}")
    public ResponseEntity<?> obtenerJugadores(@PathVariable String codigo) {
        if (!codigosActivos.contains(codigo)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Código no encontrado");
        }

        return ResponseEntity.ok(infoJugadoresPorCodigo.getOrDefault(codigo, new HashMap<>()));
    }

    @DeleteMapping("/salir")
    public ResponseEntity<?> salirDePartida(@RequestBody Map<String, String> body) {
        String codigo = body.get("codigo");
        String nombre = body.get("nombre");

        if (!codigosActivos.contains(codigo)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Código no encontrado");
        }

        jugadoresPorCodigo.getOrDefault(codigo, new ArrayList<>()).remove(nombre);
        infoJugadoresPorCodigo.getOrDefault(codigo, new HashMap<>()).remove(nombre);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/iniciar")
    public ResponseEntity<?> iniciarPartida(@RequestBody Map<String, String> body) {
        String codigo = body.get("codigo");
        String nombre = body.get("nombre");

        if (!codigosActivos.contains(codigo)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Código no encontrado");
        }

        if (!"admin".equals(infoJugadoresPorCodigo.getOrDefault(codigo, new HashMap<>()).get(nombre))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Solo el administrador puede iniciar la partida");
        }

        partidasIniciadas.add(codigo);
        preguntaActualPorCodigo.put(codigo, 0);
        tiempoInicioPreguntaPorCodigo.put(codigo, System.currentTimeMillis());

        return ResponseEntity.ok(Map.of("message", "Partida iniciada"));
    }

    @GetMapping("/estado/{codigo}")
    public ResponseEntity<?> estadoPartida(@PathVariable String codigo) {
        boolean iniciada = partidasIniciadas.contains(codigo);
        return ResponseEntity.ok(Map.of("iniciada", iniciada));
    }

    @GetMapping("/pregunta/{codigo}")
    public ResponseEntity<?> obtenerPreguntaActual(@PathVariable String codigo) {
        if (!partidasIniciadas.contains(codigo)) {
            return ResponseEntity.badRequest().body(Map.of("error", "La partida no ha comenzado"));
        }

        int index = preguntaActualPorCodigo.getOrDefault(codigo, 0);
        Pregunta pregunta = preguntaService.obtenerPreguntaPorIndice(index);

        if (pregunta == null) {
            return ResponseEntity.ok(Map.of("fin", true));
        }

        long inicio = tiempoInicioPreguntaPorCodigo.getOrDefault(codigo, System.currentTimeMillis());
        long ahora = System.currentTimeMillis();
        long transcurrido = (ahora - inicio) / 1000;
        int restante = Math.max(0, DURACION_PREGUNTA_SEGUNDOS - (int) transcurrido);

        if (restante == 0) {
            if (index >= 3) {
                return ResponseEntity.ok(Map.of("fin", true));
            }
            preguntaActualPorCodigo.put(codigo, index + 1);
            tiempoInicioPreguntaPorCodigo.put(codigo, System.currentTimeMillis());

            Pregunta siguiente = preguntaService.obtenerPreguntaPorIndice(index + 1);
            if (siguiente == null) {
                return ResponseEntity.ok(Map.of("fin", true));
            }

            List<Opcion> opcionesSiguiente = new ArrayList<>(siguiente.getOpciones());
            Collections.shuffle(opcionesSiguiente);

            return ResponseEntity.ok(Map.of(
                    "enunciado", siguiente.getEnunciado(),
                    "opciones", opcionesSiguiente,
                    "tiempoRestante", DURACION_PREGUNTA_SEGUNDOS,
                    "nuevaPregunta", true
            ));
        }

        return ResponseEntity.ok(Map.of(
                "enunciado", pregunta.getEnunciado(),
                "opciones", pregunta.getOpciones(),
                "tiempoRestante", restante,
                "nuevaPregunta", false
        ));
    }


    @PostMapping("/respuesta")
    public ResponseEntity<?> registrarRespuesta(@RequestBody Map<String, String> body) {
        String codigo = body.get("codigo");
        String nombre = body.get("nombre");
        String respuestaJugador = body.get("respuesta");

        int index = preguntaActualPorCodigo.getOrDefault(codigo, 0);
        Pregunta pregunta = preguntaService.obtenerPreguntaPorIndice(index);

        if (pregunta == null) {
            return ResponseEntity.badRequest().body("No hay pregunta activa para este código");
        }

        // Debug detallado
        System.out.println("\n----- Debug de comparación -----");
        System.out.println("Respuesta del jugador: '" + respuestaJugador + "'");
        System.out.println("Opciones disponibles:");
        pregunta.getOpciones().forEach(op ->
                System.out.println("-> '" + op.getTexto() + "' (Correcta: " + op.isCorrecta() + ")")
        );

        boolean esCorrecta = pregunta.getOpciones().stream()
                .anyMatch(op -> {
                    boolean textoCoincide = op.getTexto().trim().equalsIgnoreCase(respuestaJugador.trim());
                    boolean esOpcionCorrecta = op.isCorrecta(); // Cambiado el nombre de la variable aquí
                    System.out.println("Comparando '" + op.getTexto() + "' con '" + respuestaJugador +
                            "': texto=" + textoCoincide + ", correcta=" + esOpcionCorrecta);
                    return textoCoincide && esOpcionCorrecta;
                });

        System.out.println("Resultado final: " + esCorrecta + "\n");

        if (esCorrecta) {
            puntajesPorCodigo.getOrDefault(codigo, new HashMap<>())
                    .merge(nombre, 5, Integer::sum);
        }

        return ResponseEntity.ok(Map.of(
                "correcta", esCorrecta,
                "puntaje", puntajesPorCodigo.getOrDefault(codigo, new HashMap<>()).getOrDefault(nombre, 0)
        ));
    }


    @GetMapping("/puntajes/{codigo}")
    public ResponseEntity<?> obtenerPuntajes(@PathVariable String codigo) {
        Map<String, Integer> puntajes = puntajesPorCodigo.getOrDefault(codigo, new HashMap<>());
        return ResponseEntity.ok(puntajes);
    }

}

