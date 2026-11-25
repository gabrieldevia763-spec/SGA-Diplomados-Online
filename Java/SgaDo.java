import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

// Requiere Java 15+ (usa bloques de texto para el menu).


// ===================== Programas academicos =====================

abstract class ProgramaAcademico {
    public abstract String getNombre();

    public static double promedio(List<Double> notas) {
        if (notas.isEmpty()) return 0.0;
        double suma = 0;
        for (double n : notas) suma += n;
        return suma / notas.size();
    }

    /** Devuelve true si las notas aprueban segun la regla del programa. */
    public abstract boolean evaluarAprobacion(List<Double> notas);

    /** Texto que explica por que se aprueba (para reportes). */
    public abstract String descripcionRegla();
}

class Curso extends ProgramaAcademico {
    @Override public String getNombre() { return "Curso"; }

    @Override public boolean evaluarAprobacion(List<Double> notas) {
        return promedio(notas) >= 10;
    }

    @Override public String descripcionRegla() { return "Cumple promedio >= 10"; }
}

class Diplomado extends ProgramaAcademico {
    @Override public String getNombre() { return "Diplomado"; }

    @Override public boolean evaluarAprobacion(List<Double> notas) {
        return promedio(notas) >= 14;
    }

    @Override public String descripcionRegla() { return "Cumple promedio >= 14"; }
}

class Bootcamp extends ProgramaAcademico {
    @Override public String getNombre() { return "Bootcamp"; }

    @Override public boolean evaluarAprobacion(List<Double> notas) {
        for (double n : notas) {
            if (n < 14) return false;
        }
        return true;
    }

    @Override public String descripcionRegla() { return "Cumple regla de ninguna nota < 14"; }
}

// ===================== Personas =====================

class Persona {
    private final String cedula;
    private final String nombre;
    private final String correo;

    public Persona(String cedula, String nombre, String correo) {
        this.cedula = cedula;
        this.nombre = nombre;
        this.correo = correo;
    }

    public String getCedula() { return cedula; }
    public String getNombre() { return nombre; }
    public String getCorreo() { return correo; }
}

class Alumno extends Persona {
    private final ProgramaAcademico programa;
    private final List<Double> notas;

    public Alumno(String cedula, String nombre, String correo,
                  ProgramaAcademico programa, List<Double> notas) {
        super(cedula, nombre, correo);
        this.programa = programa;
        this.notas = notas == null ? new ArrayList<>() : new ArrayList<>(notas);
    }

    public Alumno(String cedula, String nombre, String correo, ProgramaAcademico programa) {
        this(cedula, nombre, correo, programa, null);
    }

    public ProgramaAcademico getPrograma() { return programa; }

    public List<Double> getNotas() { return new ArrayList<>(notas); }

    public void agregarNota(double nota) {
        if (notas.size() >= SgaDo.MAX_NOTAS) {
            throw new IllegalStateException(
                    "El alumno ya tiene las " + SgaDo.MAX_NOTAS + " notas permitidas.");
        }
        notas.add(nota);
    }

    /** Quita y devuelve la ultima nota, o null si no hay notas. */
    public Double quitarUltimaNota() {
        return notas.isEmpty() ? null : notas.remove(notas.size() - 1);
    }

    public boolean tieneNotasCompletas() { return notas.size() >= SgaDo.MAX_NOTAS; }

    public double promedio() { return ProgramaAcademico.promedio(notas); }

    public boolean aprobado() {
        return tieneNotasCompletas() && programa.evaluarAprobacion(notas);
    }

    public String estatus() {
        if (!tieneNotasCompletas()) return "PENDIENTE (notas incompletas)";
        return aprobado() ? "APROBADO" : "REPROBADO";
    }

    public String aLinea() {
        List<String> txt = new ArrayList<>();
        for (int i = 0; i < SgaDo.MAX_NOTAS; i++) {
            txt.add(i < notas.size() ? SgaDo.fmtG(notas.get(i)) : "0");
        }
        return getCedula() + "," + getNombre() + "," + getCorreo() + ","
                + programa.getNombre() + "," + String.join(",", txt);
    }
}

class Profesor extends Persona {
    private final String especialidad;
    private final String materia;

    public Profesor(String cedula, String nombre, String correo,
                    String especialidad, String materia) {
        super(cedula, nombre, correo);
        this.especialidad = especialidad;
        this.materia = materia;
    }

    public String getEspecialidad() { return especialidad; }
    public String getMateria() { return materia; }

    public String aLinea() {
        return getCedula() + "," + getNombre() + "," + getCorreo() + ","
                + especialidad + "," + materia;
    }
}

// ===================== Programa principal =====================

public class SgaDo {

    static final String NOMBRE_FUENTE = "SgaDo.java";

    // IMPORTANTE: BASE_DIR debe declararse antes que los archivos que dependen de el.
    static final Path BASE_DIR = carpetaDelCodigo();
    static final Path ARCHIVO_ALUMNOS = BASE_DIR.resolve("alumnos.txt");
    static final Path ARCHIVO_PROFESORES = BASE_DIR.resolve("profesores.txt");
    static final Path ARCHIVO_CERTIFICADOS = BASE_DIR.resolve("certificados_pendientes.txt");

    static final int MAX_NOTAS = 3;
    static final String MSG_NUMERICO = "Error: Ingrese un valor numerico valido";

    private static final BufferedReader IN =
            new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

    
    static class EntradaFinalizada extends RuntimeException {}

    // ---------- Ubicacion de la carpeta del codigo ----------

    /** True si la carpeta contiene SgaDo.java. */
    static boolean contieneFuente(Path carpeta) {
        return carpeta != null && Files.isRegularFile(carpeta.resolve(NOMBRE_FUENTE));
    }

    /** Evita recorrer carpetas pesadas o irrelevantes al buscar SgaDo.java. */
    static boolean carpetaIgnorada(Path p) {
        Path nombre = p.getFileName();
        if (nombre == null) return false;
        String n = nombre.toString();
        return n.startsWith(".") || n.equals("node_modules");
    }

    /** Busca SgaDo.java hacia abajo desde 'inicio' (hasta 'profundidad' niveles). */
    static Optional<Path> buscarHaciaAbajo(Path inicio, int profundidad) {
        if (inicio == null || !Files.isDirectory(inicio)) return Optional.empty();
        try (Stream<Path> s = Files.walk(inicio, profundidad)) {
            return s.filter(p -> p.getFileName() != null
                            && p.getFileName().toString().equals(NOMBRE_FUENTE)
                            && Files.isRegularFile(p))
                    .filter(p -> {
                        // descarta resultados dentro de carpetas ocultas / node_modules
                        for (Path q = p.getParent(); q != null && !q.equals(inicio); q = q.getParent()) {
                            if (carpetaIgnorada(q)) return false;
                        }
                        return true;
                    })
                    .min((a, b) -> Integer.compare(a.getNameCount(), b.getNameCount()))
                    .map(Path::getParent);
        } catch (IOException | UncheckedIOException e) {
            return Optional.empty();
        }
    }

    /**
     * Devuelve la carpeta donde esta SgaDo.java. Orden de busqueda:
     *  1. Carpeta del .class / .jar (si ahi esta el .java).
     *  2. Directorio actual (si ahi esta el .java).
     *  3. Busqueda hacia abajo desde el directorio actual.
     *  4. Busqueda desde la carpeta del .class y sus padres cercanos.
     *  5. Si nada funciona, la carpeta del .class o el directorio actual.
     */
    static Path carpetaDelCodigo() {
        Path cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath();

        Path carpetaClase = null;
        try {
            Path ub = Paths.get(SgaDo.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).toAbsolutePath();
            carpetaClase = Files.isDirectory(ub) ? ub : ub.getParent();
        } catch (Exception e) {
            // sin ubicacion de clase (por ejemplo, "java SgaDo.java"): se usa el directorio actual
        }

        // 1 y 2: coincidencias directas
        if (contieneFuente(carpetaClase)) return carpetaClase;
        if (contieneFuente(cwd)) return cwd;

        // 3: buscar hacia abajo desde el directorio actual
        Optional<Path> abajo = buscarHaciaAbajo(cwd, 6);
        if (abajo.isPresent()) return abajo.get();

        // 4: buscar desde la carpeta del .class y sus padres cercanos
        Path p = carpetaClase;
        for (int i = 0; i < 3 && p != null; i++) {
            Optional<Path> r = buscarHaciaAbajo(p, 4);
            if (r.isPresent()) return r.get();
            p = p.getParent();
        }

        // 5: ultimo recurso
        return carpetaClase != null ? carpetaClase : cwd;
    }

 

    

    // ---------- Utilidades ----------

    static String input(String mensaje) {
        System.out.print(mensaje);
        System.out.flush();
        try {
            String linea = IN.readLine();
            if (linea == null) throw new EntradaFinalizada();
            return linea;
        } catch (IOException e) {
            throw new EntradaFinalizada();
        }
    }

    /** Promedio parcial a 1 decimal (13.67 -> 13.6) */
    static String fmtProm(double valor) {
        double parcial = Math.floor(valor * 10 + 1e-9) / 10;
        return String.format(Locale.US, "%.1f", parcial);
    }

    /** Redondea los decimales (14.0 -> "14", 13.5 -> "13.5"). */
    static String fmtG(double n) {
        BigDecimal bd = new BigDecimal(n).round(new MathContext(6)).stripTrailingZeros();
        if (bd.signum() == 0) return "0";
        return bd.toPlainString();
    }

    static ProgramaAcademico crearPrograma(String tipo) {
        switch (tipo.strip().toLowerCase()) {
            case "curso": return new Curso();
            case "diplomado": return new Diplomado();
            case "bootcamp": return new Bootcamp();
            default: return null;
        }
    }

    // ---------- Carga y guardado ----------

    /** Escribe un archivo temporal y lo reemplaza: evita archivos corruptos
     *  si el programa se cierra a mitad de la escritura. */
    static void escribirAtomico(Path ruta, List<String> lineas) {
        Path tmp = Paths.get(ruta.toString() + ".tmp");
        try {
            try (FileOutputStream fos = new FileOutputStream(tmp.toFile());
                 OutputStreamWriter w = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                for (String linea : lineas) {
                    w.write(linea);
                    w.write("\n");
                }
                w.flush();
                fos.getFD().sync();
            }
            try {
                Files.move(tmp, ruta, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, ruta, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void guardarAlumnos(List<Alumno> alumnos) {
        List<String> lineas = new ArrayList<>();
        for (Alumno a : alumnos) lineas.add(a.aLinea());
        escribirAtomico(ARCHIVO_ALUMNOS, lineas);
    }

    static void guardarProfesores(List<Profesor> profesores) {
        List<String> lineas = new ArrayList<>();
        for (Profesor p : profesores) lineas.add(p.aLinea());
        escribirAtomico(ARCHIVO_PROFESORES, lineas);
    }

    static List<Alumno> cargarAlumnos() {
        List<Alumno> alumnos = new ArrayList<>();
        if (!Files.exists(ARCHIVO_ALUMNOS)) return alumnos;
        try {
            List<String> lineas = Files.readAllLines(ARCHIVO_ALUMNOS, StandardCharsets.UTF_8);
            int n = 0;
            for (String linea : lineas) {
                n++;
                linea = linea.strip();
                if (linea.isEmpty()) continue;
                try {
                    String[] partes = linea.split(",", -1);
                    for (int i = 0; i < partes.length; i++) partes[i] = partes[i].strip();
                    if (partes.length < 4) {
                        throw new IllegalArgumentException("faltan campos");
                    }
                    String cedula = partes[0], nombre = partes[1],
                            correo = partes[2], tipo = partes[3];
                    ProgramaAcademico programa = crearPrograma(tipo);
                    if (programa == null) {
                        throw new IllegalArgumentException("programa desconocido '" + tipo + "'");
                    }
                    // Los ceros aparecen como "sin nota registrada".
                    List<Double> notas = new ArrayList<>();
                    int fin = Math.min(partes.length, 4 + MAX_NOTAS);
                    for (int i = 4; i < fin; i++) {
                        double v = Double.parseDouble(partes[i]);
                        if (v != 0) notas.add(v);
                    }
                    alumnos.add(new Alumno(cedula, nombre, correo, programa, notas));
                } catch (IllegalArgumentException e) {
                    System.out.println("Aviso: linea " + n + " de alumnos.txt ignorada ("
                            + e.getMessage() + ").");
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return alumnos;
    }

    static List<Profesor> cargarProfesores() {
        List<Profesor> profesores = new ArrayList<>();
        if (!Files.exists(ARCHIVO_PROFESORES)) return profesores;
        try {
            List<String> lineas = Files.readAllLines(ARCHIVO_PROFESORES, StandardCharsets.UTF_8);
            int n = 0;
            for (String linea : lineas) {
                n++;
                linea = linea.strip();
                if (linea.isEmpty()) continue;
                String[] partes = linea.split(",", -1);
                for (int i = 0; i < partes.length; i++) partes[i] = partes[i].strip();
                if (partes.length < 5) {
                    System.out.println("Aviso: linea " + n + " de profesores.txt ignorada.");
                    continue;
                }
                profesores.add(new Profesor(partes[0], partes[1], partes[2], partes[3], partes[4]));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return profesores;
    }

    // ---------- Entrada de datos ----------

    /** Pide un texto no vacio; las comas se eliminan  */
    static String pedirTexto(String mensaje) {
        while (true) {
            String valor = input(mensaje).replace(",", " ").strip();
            if (!valor.isEmpty()) {
                return String.join(" ", valor.split("\\s+"));
            }
            System.out.println("Error: Este campo no puede estar vacio.");
        }
    }

    static ProgramaAcademico pedirPrograma() {
        while (true) {
            String valor = input("Tipo de Programa (Curso/Diplomado/Bootcamp): ");
            ProgramaAcademico programa = crearPrograma(valor);
            if (programa != null) return programa;
            System.out.println("Error: Elija Curso, Diplomado o Bootcamp.");
        }
    }

    static Alumno buscarAlumno(List<Alumno> alumnos, String cedula) {
        for (Alumno a : alumnos) {
            if (a.getCedula().equalsIgnoreCase(cedula)) return a;
        }
        return null;
    }

    // ---------- Opciones del menu ----------

    static void registrarAlumno(List<Alumno> alumnos) {
        String cedula = pedirTexto("Cedula/ID: ").toUpperCase();
        if (buscarAlumno(alumnos, cedula) != null) {
            System.out.println("Error: Ya existe un alumno con la cedula " + cedula + ".");
            return;
        }
        String nombre = pedirTexto("Nombre completo: ");
        String correo = pedirTexto("Correo electronico: ");
        ProgramaAcademico programa = pedirPrograma();
        alumnos.add(new Alumno(cedula, nombre, correo, programa));
        guardarAlumnos(alumnos);
        System.out.println("Alumno " + nombre + " registrado y guardado en alumnos.txt.");
    }

    static void registrarProfesor(List<Profesor> profesores) {
        String cedula = pedirTexto("Cedula/ID: ").toUpperCase();
        for (Profesor p : profesores) {
            if (p.getCedula().equalsIgnoreCase(cedula)) {
                System.out.println("Error: Ya existe un profesor con la cedula " + cedula + ".");
                return;
            }
        }
        String nombre = pedirTexto("Nombre completo: ");
        String correo = pedirTexto("Correo electronico: ");
        String especialidad = pedirTexto("Especialidad: ");
        String materia = pedirTexto("Materia asignada: ");
        profesores.add(new Profesor(cedula, nombre, correo, especialidad, materia));
        guardarProfesores(profesores);
        System.out.println("Profesor " + nombre + " registrado y guardado en profesores.txt.");
    }

    static String listaNotas(List<Double> notas, String separador) {
        List<String> txt = new ArrayList<>();
        for (double n : notas) txt.add(fmtG(n));
        return String.join(separador, txt);
    }

    static void registrarNota(List<Alumno> alumnos, Deque<String> pila) {
        String cedula = pedirTexto("Cedula del alumno: ");
        Alumno alumno = buscarAlumno(alumnos, cedula);
        if (alumno == null) {
            System.out.println("Error: No se encontro un alumno con esa cedula.");
            return;
        }
        List<Double> actuales = alumno.getNotas();
        System.out.println("Alumno: " + alumno.getNombre() + " ("
                + alumno.getPrograma().getNombre() + ") - Notas actuales: "
                + (actuales.isEmpty() ? "ninguna" : "[" + listaNotas(actuales, ", ") + "]"));
        if (alumno.tieneNotasCompletas()) {
            System.out.println("Error: El alumno ya tiene las " + MAX_NOTAS + " notas permitidas.");
            return;
        }
        // Se piden todas las notas que faltan, una tras otra, sin volver al menu.
        int faltantes = MAX_NOTAS - actuales.size();
        for (int i = 0; i < faltantes; i++) {
            int numero = alumno.getNotas().size() + 1;
            double nota;
            while (true) {
                String texto = input("Nota " + numero + " de " + MAX_NOTAS + ": ");
                texto = texto.strip().replace(",", ".");
                try {
                    nota = Double.parseDouble(texto);
                } catch (NumberFormatException e) {
                    System.out.println(MSG_NUMERICO);
                    continue;
                }
                if (Double.isNaN(nota) || Double.isInfinite(nota)) {
                    System.out.println(MSG_NUMERICO);
                    continue;
                }
                if (nota <= 0) {
                    System.out.println("Error: La nota debe ser mayor que 0.");
                    continue;
                }
                if (nota < 1 || nota > 20) {
                    System.out.println("Error: La nota debe estar entre 1 y 20.");
                    continue;
                }
                break;
            }
            alumno.agregarNota(nota);
            guardarAlumnos(alumnos); // se guarda nota por nota
            pila.push(alumno.getCedula());
        }
        System.out.println("Notas registradas para " + alumno.getNombre() + ": "
                + listaNotas(alumno.getNotas(), ", ") + ".");
    }

    static void deshacerNota(List<Alumno> alumnos, Deque<String> pila) {
        if (pila.isEmpty()) {
            System.out.println("No hay registros de notas para deshacer.");
            return;
        }
        String cedula = pila.pop(); // LIFO
        Alumno alumno = buscarAlumno(alumnos, cedula);
        if (alumno == null) {
            System.out.println("Error: El alumno de ese registro ya no existe.");
            return;
        }
        Double nota = alumno.quitarUltimaNota();
        guardarAlumnos(alumnos);
        System.out.println("Se deshizo la nota " + (nota == null ? "None" : fmtG(nota))
                + " de " + alumno.getNombre() + ".");
    }

    static void generarColaCertificados() {
        List<Alumno> alumnos = cargarAlumnos(); // se analiza lo que hay en el archivo
        Deque<Alumno> cola = new ArrayDeque<>();
        for (Alumno a : alumnos) {
            if (a.aprobado()) cola.addLast(a);
        }

        String igual = "=".repeat(41);
        List<String> lineas = new ArrayList<>();
        lineas.add(igual);
        lineas.add("REPORTE DE CERTIFICADOS PENDIENTES");
        lineas.add(igual);
        lineas.add("Total de graduandos en cola: " + cola.size());
        lineas.add("");

        int posicion = 1;
        while (!cola.isEmpty()) {
            Alumno a = cola.pollFirst(); // FIFO
            lineas.add(posicion + ". [" + a.getCedula() + "] " + a.getNombre());
            lineas.add("   - Programa: " + a.getPrograma().getNombre());
            lineas.add("   - Promedio Final: " + fmtProm(a.promedio()));
            lineas.add("   - Estatus: APROBADO (" + a.getPrograma().descripcionRegla() + ")");
            lineas.add("");
            posicion++;
        }
        lineas.add(igual);
        lineas.add("* Fin del reporte - Generado por SGA-DO *");
        escribirAtomico(ARCHIVO_CERTIFICADOS, lineas);
        System.out.println("Cola procesada: " + (posicion - 1)
                + " graduando(s) exportado(s) a certificados_pendientes.txt.");
    }

    static void mostrarReporte() {
        List<Profesor> profesores = cargarProfesores();
        List<Alumno> alumnos = cargarAlumnos();
        System.out.println("\n--- PROFESORES ACTIVOS ---");
        if (profesores.isEmpty()) System.out.println("(sin profesores registrados)");
        for (Profesor p : profesores) {
            System.out.println("[" + p.getCedula() + "] " + p.getNombre() + " | " + p.getCorreo()
                    + " | Especialidad: " + p.getEspecialidad() + " | Materia: " + p.getMateria());
        }
        System.out.println("\n--- ALUMNOS REGISTRADOS ---");
        if (alumnos.isEmpty()) System.out.println("(sin alumnos registrados)");
        for (Alumno a : alumnos) {
            List<Double> n = a.getNotas();
            String notas = n.isEmpty() ? "sin notas" : listaNotas(n, ", ");
            String prom = n.isEmpty() ? "-" : fmtProm(a.promedio());
            System.out.println("[" + a.getCedula() + "] " + a.getNombre() + " | "
                    + a.getPrograma().getNombre() + " | Notas: " + notas
                    + " | Promedio: " + prom + " | Estatus: " + a.estatus());
        }
    }

    // ---------- Menu principal ----------

    static final String MENU = """

            ==================================================
                    SGA-DO: SISTEMA DIPLOMADOSONLINE
            ==================================================
            1. Registrar Alumno
            2. Registrar Profesor
            3. Registrar Notas a un Alumno
            4. Deshacer Ultimo Registro de Nota
            5. Generar Cola de Certificados
            6. Mostrar Reporte General
            7. Salir
            ==================================================""";

    public static void main(String[] args) {
        System.out.println("Carpeta de datos: " + BASE_DIR);

        List<Alumno> alumnos = cargarAlumnos();
        List<Profesor> profesores = cargarProfesores();
        Deque<String> pilaNotas = new ArrayDeque<>(); // Pila LIFO para "deshacer"

        while (true) {
            System.out.println(MENU);
            try {
                String entrada = input("Seleccione una opcion (1-7): ").strip();
                int opcion;
                try {
                    opcion = Integer.parseInt(entrada);
                } catch (NumberFormatException e) {
                    System.out.println(MSG_NUMERICO);
                    continue;
                }

                switch (opcion) {
                    case 1: registrarAlumno(alumnos); break;
                    case 2: registrarProfesor(profesores); break;
                    case 3: registrarNota(alumnos, pilaNotas); break;
                    case 4: deshacerNota(alumnos, pilaNotas); break;
                    case 5: generarColaCertificados(); break;
                    case 6: mostrarReporte(); break;
                    case 7:
                        guardarAlumnos(alumnos);
                        guardarProfesores(profesores);
                        alumnos.clear();
                        profesores.clear();
                        pilaNotas.clear();
                        System.out.println("Datos guardados. Hasta pronto.");
                        return;
                    default:
                        System.out.println("Error: Seleccione una opcion entre 1 y 7.");
                }
            } catch (EntradaFinalizada e) {
                System.out.println("\nEntrada finalizada. Cerrando de forma segura.");
                return;
            } catch (UncheckedIOException e) {
                System.out.println("Error de archivo: " + e.getMessage());
            }
        }
    }
}
