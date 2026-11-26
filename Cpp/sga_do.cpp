// SGA-DO: Sistema DiplomadosOnline //


#include <algorithm>
#include <cctype>
#include <cerrno>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <iomanip>
#include <iostream>
#include <memory>
#include <optional>
#include <queue>
#include <sstream>
#include <stack>
#include <stdexcept>
#include <string>
#include <vector>

#ifdef _WIN32
#ifndef NOMINMAX
#define NOMINMAX
#endif
#include <io.h>
#include <windows.h>
#else
#include <unistd.h>
#endif

namespace fs = std::filesystem;

// Rutas de los archivos 
std::string ARCHIVO_ALUMNOS = "alumnos.txt";
std::string ARCHIVO_PROFESORES = "profesores.txt";
std::string ARCHIVO_CERTIFICADOS = "certificados_pendientes.txt";

constexpr std::size_t MAX_NOTAS = 3;
const std::string MSG_NUMERICO = "Error: Ingrese un valor numerico valido";

// Se lanza cuando se cierra la entrada 
struct FinDeEntrada {};

// ---------------------------------------------------------------------------
// Utilidades de texto
// ---------------------------------------------------------------------------

std::string recortar(const std::string& s) {
    const char* espacios = " \t\r\n\f\v";
    const auto inicio = s.find_first_not_of(espacios);
    if (inicio == std::string::npos) return "";
    const auto fin = s.find_last_not_of(espacios);
    return s.substr(inicio, fin - inicio + 1);
}

std::string a_minusculas(std::string s) {
    for (char& c : s)
        c = static_cast<char>(std::tolower(static_cast<unsigned char>(c)));
    return s;
}

std::string a_mayusculas(std::string s) {
    for (char& c : s)
        c = static_cast<char>(std::toupper(static_cast<unsigned char>(c)));
    return s;
}

// Divide por comas, conservando los campos vacios, y recorta cada uno
std::vector<std::string> dividir(const std::string& linea) {
    std::vector<std::string> partes;
    std::size_t inicio = 0;
    while (true) {
        const auto coma = linea.find(',', inicio);
        if (coma == std::string::npos) {
            partes.push_back(recortar(linea.substr(inicio)));
            return partes;
        }
        partes.push_back(recortar(linea.substr(inicio, coma - inicio)));
        inicio = coma + 1;
    }
}

// Convierte TODO el texto a numero
std::optional<double> a_numero(const std::string& texto) {
    const std::string t = recortar(texto);
    if (t.empty() || t.find_first_of("xX") != std::string::npos)
        return std::nullopt;
    char* fin = nullptr;
    const double valor = std::strtod(t.c_str(), &fin);
    if (fin != t.c_str() + t.size() || !std::isfinite(valor))
        return std::nullopt;
    return valor;
}

// Equivale al formato {n:g} de Python
std::string fmt_g(double valor) {
    std::ostringstream os;
    os << valor;
    return os.str();
}

// Promedio parcial a 1 decimal (13.67 -> 13.6)
std::string fmt_prom(double valor) {
    std::ostringstream os;
    os << std::fixed << std::setprecision(1)
       << std::floor(valor * 10 + 1e-9) / 10;
    return os.str();
}

std::string unir_notas(const std::vector<double>& notas,
                       const std::string& separador) {
    std::string texto;
    for (std::size_t i = 0; i < notas.size(); ++i) {
        if (i > 0) texto += separador;
        texto += fmt_g(notas[i]);
    }
    return texto;
}

std::string leer_linea(const std::string& mensaje) {
    std::cout << mensaje << std::flush;
    std::string linea;
    if (!std::getline(std::cin, linea)) throw FinDeEntrada{};
    return linea;
}

// ---------------------------------------------------------------------------
// Programas academicos
// ---------------------------------------------------------------------------

class ProgramaAcademico {
public:
    virtual ~ProgramaAcademico() = default;

    static double promedio(const std::vector<double>& notas) {
        if (notas.empty()) return 0.0;
        double suma = 0.0;
        for (double n : notas) suma += n;
        return suma / static_cast<double>(notas.size());
    }

    virtual std::string nombre() const = 0;
    // Devuelve true si las notas aprueban segun la regla del programa.
    virtual bool evaluar_aprobacion(const std::vector<double>& notas) const = 0;
    // Texto que explica por que se aprueba (para reportes).
    virtual std::string descripcion_regla() const = 0;
};

class Curso : public ProgramaAcademico {
public:
    std::string nombre() const override { return "Curso"; }
    bool evaluar_aprobacion(const std::vector<double>& notas) const override {
        return promedio(notas) >= 10;
    }
    std::string descripcion_regla() const override {
        return "Cumple promedio >= 10";
    }
};

class Diplomado : public ProgramaAcademico {
public:
    std::string nombre() const override { return "Diplomado"; }
    bool evaluar_aprobacion(const std::vector<double>& notas) const override {
        return promedio(notas) >= 14;
    }
    std::string descripcion_regla() const override {
        return "Cumple promedio >= 14";
    }
};

class Bootcamp : public ProgramaAcademico {
public:
    std::string nombre() const override { return "Bootcamp"; }
    bool evaluar_aprobacion(const std::vector<double>& notas) const override {
        return std::all_of(notas.begin(), notas.end(),
                           [](double n) { return n >= 14; });
    }
    std::string descripcion_regla() const override {
        return "Cumple regla de ninguna nota < 14";
    }
};

// Devuelve nullptr si el tipo no existe
std::unique_ptr<ProgramaAcademico> crear_programa(const std::string& tipo) {
    const std::string clave = a_minusculas(recortar(tipo));
    if (clave == "curso") return std::make_unique<Curso>();
    if (clave == "diplomado") return std::make_unique<Diplomado>();
    if (clave == "bootcamp") return std::make_unique<Bootcamp>();
    return nullptr;
}

// ---------------------------------------------------------------------------
// Super clase Persona y subclases Alumno y Profesor
// ---------------------------------------------------------------------------

class Persona {
public:
    Persona(std::string cedula, std::string nombre, std::string correo)
        : cedula_(std::move(cedula)),
          nombre_(std::move(nombre)),
          correo_(std::move(correo)) {}

    const std::string& cedula() const { return cedula_; }
    const std::string& nombre() const { return nombre_; }
    const std::string& correo() const { return correo_; }

protected:
    std::string cedula_;
    std::string nombre_;
    std::string correo_;
};

class Alumno : public Persona {
public:
    Alumno(std::string cedula, std::string nombre, std::string correo,
           std::unique_ptr<ProgramaAcademico> programa,
           std::vector<double> notas = {})
        : Persona(std::move(cedula), std::move(nombre), std::move(correo)),
          programa_(std::move(programa)),
          notas_(std::move(notas)) {}

    const ProgramaAcademico& programa() const { return *programa_; }
    const std::vector<double>& notas() const { return notas_; }

    void agregar_nota(double nota) {
        if (notas_.size() >= MAX_NOTAS)
            throw std::length_error("El alumno ya tiene las " +
                                    std::to_string(MAX_NOTAS) +
                                    " notas permitidas.");
        notas_.push_back(nota);
    }

    std::optional<double> quitar_ultima_nota() {
        if (notas_.empty()) return std::nullopt;
        const double nota = notas_.back();
        notas_.pop_back();
        return nota;
    }

    bool tiene_notas_completas() const { return notas_.size() >= MAX_NOTAS; }

    double promedio() const { return programa_->promedio(notas_); }

    bool aprobado() const {
        return tiene_notas_completas() &&
               programa_->evaluar_aprobacion(notas_);
    }

    std::string estatus() const {
        if (!tiene_notas_completas()) return "PENDIENTE (notas incompletas)";
        return aprobado() ? "APROBADO" : "REPROBADO";
    }

    std::string a_linea() const {
        std::vector<double> completas = notas_;
        if (completas.size() < MAX_NOTAS) completas.resize(MAX_NOTAS, 0.0);
        return cedula_ + "," + nombre_ + "," + correo_ + "," +
               programa_->nombre() + "," + unir_notas(completas, ",");
    }

private:
    std::unique_ptr<ProgramaAcademico> programa_;
    std::vector<double> notas_;
};

class Profesor : public Persona {
public:
    Profesor(std::string cedula, std::string nombre, std::string correo,
             std::string especialidad, std::string materia)
        : Persona(std::move(cedula), std::move(nombre), std::move(correo)),
          especialidad_(std::move(especialidad)),
          materia_(std::move(materia)) {}

    const std::string& especialidad() const { return especialidad_; }
    const std::string& materia() const { return materia_; }

    std::string a_linea() const {
        return cedula_ + "," + nombre_ + "," + correo_ + "," +
               especialidad_ + "," + materia_;
    }

private:
    std::string especialidad_;
    std::string materia_;
};

// ---------------------------------------------------------------------------
// Carga y guardado de datos
// ---------------------------------------------------------------------------

// Escribe a un temporal y lo reemplaza: evita archivos corruptos
// si el programa se cierra a mitad de la escritura.
void escribir_atomico(const std::string& ruta,
                      const std::vector<std::string>& lineas) {
    const std::string tmp = ruta + ".tmp";
    std::FILE* f = std::fopen(tmp.c_str(), "w");
    if (!f)
        throw std::runtime_error("no se pudo crear " + tmp + ": " +
                                 std::strerror(errno));
    bool ok = true;
    for (const auto& linea : lineas) {
        const std::string completa = linea + "\n";
        if (std::fputs(completa.c_str(), f) == EOF) ok = false;
    }
    if (std::fflush(f) != 0) ok = false;
#ifdef _WIN32
    if (_commit(_fileno(f)) != 0) ok = false;
#else
    if (fsync(fileno(f)) != 0) ok = false;
#endif
    if (std::fclose(f) != 0) ok = false;
    if (!ok) throw std::runtime_error("no se pudo escribir " + tmp);

#ifdef _WIN32
    if (!MoveFileExA(tmp.c_str(), ruta.c_str(), MOVEFILE_REPLACE_EXISTING))
        throw std::runtime_error("no se pudo reemplazar " + ruta);
#else
    if (std::rename(tmp.c_str(), ruta.c_str()) != 0)
        throw std::runtime_error("no se pudo reemplazar " + ruta + ": " +
                                 std::strerror(errno));
#endif
}

void guardar_alumnos(const std::vector<Alumno>& alumnos) {
    std::vector<std::string> lineas;
    for (const auto& a : alumnos) lineas.push_back(a.a_linea());
    escribir_atomico(ARCHIVO_ALUMNOS, lineas);
}

void guardar_profesores(const std::vector<Profesor>& profesores) {
    std::vector<std::string> lineas;
    for (const auto& p : profesores) lineas.push_back(p.a_linea());
    escribir_atomico(ARCHIVO_PROFESORES, lineas);
}

std::vector<Alumno> cargar_alumnos() {
    std::vector<Alumno> alumnos;
    if (!fs::exists(ARCHIVO_ALUMNOS)) return alumnos;
    std::ifstream f(ARCHIVO_ALUMNOS);
    if (!f) throw std::runtime_error("no se pudo abrir " + ARCHIVO_ALUMNOS);

    std::string linea;
    for (int n = 1; std::getline(f, linea); ++n) {
        linea = recortar(linea);
        if (linea.empty()) continue;
        try {
            const auto partes = dividir(linea);
            if (partes.size() < 4)
                throw std::invalid_argument("faltan campos");
            auto programa = crear_programa(partes[3]);
            if (!programa)
                throw std::invalid_argument("programa desconocido '" +
                                            partes[3] + "'");
            // Los ceros aparecen como "sin nota registrada".
            std::vector<double> notas;
            for (std::size_t i = 4; i < partes.size() && i < 4 + MAX_NOTAS;
                 ++i) {
                const auto valor = a_numero(partes[i]);
                if (!valor)
                    throw std::invalid_argument("nota no valida '" +
                                                partes[i] + "'");
                if (*valor != 0) notas.push_back(*valor);
            }
            alumnos.emplace_back(partes[0], partes[1], partes[2],
                                 std::move(programa), std::move(notas));
        } catch (const std::invalid_argument& e) {
            std::cout << "Aviso: linea " << n << " de alumnos.txt ignorada ("
                      << e.what() << ").\n";
        }
    }
    return alumnos;
}

std::vector<Profesor> cargar_profesores() {
    std::vector<Profesor> profesores;
    if (!fs::exists(ARCHIVO_PROFESORES)) return profesores;
    std::ifstream f(ARCHIVO_PROFESORES);
    if (!f)
        throw std::runtime_error("no se pudo abrir " + ARCHIVO_PROFESORES);

    std::string linea;
    for (int n = 1; std::getline(f, linea); ++n) {
        linea = recortar(linea);
        if (linea.empty()) continue;
        const auto partes = dividir(linea);
        if (partes.size() < 5) {
            std::cout << "Aviso: linea " << n
                      << " de profesores.txt ignorada.\n";
            continue;
        }
        profesores.emplace_back(partes[0], partes[1], partes[2], partes[3],
                                partes[4]);
    }
    return profesores;
}

// ---------------------------------------------------------------------------
// Ingreso de datos
// ---------------------------------------------------------------------------

// Pide un texto no vacio; las comas se eliminan.
std::string pedir_texto(const std::string& mensaje) {
    while (true) {
        std::string valor = leer_linea(mensaje);
        std::replace(valor.begin(), valor.end(), ',', ' ');
        // Une las palabras dejando un solo espacio entre ellas
        std::istringstream palabras(valor);
        std::string palabra, resultado;
        while (palabras >> palabra) {
            if (!resultado.empty()) resultado += ' ';
            resultado += palabra;
        }
        if (!resultado.empty()) return resultado;
        std::cout << "Error: Este campo no puede estar vacio.\n";
    }
}

std::unique_ptr<ProgramaAcademico> pedir_programa() {
    while (true) {
        auto programa = crear_programa(
            leer_linea("Tipo de Programa (Curso/Diplomado/Bootcamp): "));
        if (programa) return programa;
        std::cout << "Error: Elija Curso, Diplomado o Bootcamp.\n";
    }
}

// Devuelve nullptr si no existe
Alumno* buscar_alumno(std::vector<Alumno>& alumnos, const std::string& cedula) {
    const std::string buscada = a_mayusculas(cedula);
    for (auto& a : alumnos)
        if (a_mayusculas(a.cedula()) == buscada) return &a;
    return nullptr;
}

// ---------------------------------------------------------------------------
// Opciones del menu
// ---------------------------------------------------------------------------

void registrar_alumno(std::vector<Alumno>& alumnos) {
    const std::string cedula = a_mayusculas(pedir_texto("Cedula/ID: "));
    if (buscar_alumno(alumnos, cedula)) {
        std::cout << "Error: Ya existe un alumno con la cedula " << cedula
                  << ".\n";
        return;
    }
    const std::string nombre = pedir_texto("Nombre completo: ");
    const std::string correo = pedir_texto("Correo electronico: ");
    auto programa = pedir_programa();
    alumnos.emplace_back(cedula, nombre, correo, std::move(programa));
    guardar_alumnos(alumnos);
    std::cout << "Alumno " << nombre
              << " registrado y guardado en alumnos.txt.\n";
}

void registrar_profesor(std::vector<Profesor>& profesores) {
    const std::string cedula = a_mayusculas(pedir_texto("Cedula/ID: "));
    for (const auto& p : profesores) {
        if (a_mayusculas(p.cedula()) == cedula) {
            std::cout << "Error: Ya existe un profesor con la cedula "
                      << cedula << ".\n";
            return;
        }
    }
    const std::string nombre = pedir_texto("Nombre completo: ");
    const std::string correo = pedir_texto("Correo electronico: ");
    const std::string especialidad = pedir_texto("Especialidad: ");
    const std::string materia = pedir_texto("Materia asignada: ");
    profesores.emplace_back(cedula, nombre, correo, especialidad, materia);
    guardar_profesores(profesores);
    std::cout << "Profesor " << nombre
              << " registrado y guardado en profesores.txt.\n";
}

void registrar_nota(std::vector<Alumno>& alumnos,
                    std::stack<std::string>& pila) {
    const std::string cedula = pedir_texto("Cedula del alumno: ");
    Alumno* alumno = buscar_alumno(alumnos, cedula);
    if (!alumno) {
        std::cout << "Error: No se encontro un alumno con esa cedula.\n";
        return;
    }

    std::string actuales = "ninguna";
    if (!alumno->notas().empty()) {
        actuales = "[";
        for (std::size_t i = 0; i < alumno->notas().size(); ++i) {
            if (i > 0) actuales += ", ";
            actuales += "'" + fmt_g(alumno->notas()[i]) + "'";
        }
        actuales += "]";
    }
    std::cout << "Alumno: " << alumno->nombre() << " ("
              << alumno->programa().nombre()
              << ") - Notas actuales: " << actuales << "\n";

    if (alumno->tiene_notas_completas()) {
        std::cout << "Error: El alumno ya tiene las " << MAX_NOTAS
                  << " notas permitidas.\n";
        return;
    }

    // Se piden todas las notas que faltan, una tras otra, sin volver al menu.
    const std::size_t faltantes = MAX_NOTAS - alumno->notas().size();
    for (std::size_t i = 0; i < faltantes; ++i) {
        const std::size_t numero = alumno->notas().size() + 1;
        double nota = 0.0;
        while (true) {
            std::string texto =
                leer_linea("Nota " + std::to_string(numero) + " de " +
                           std::to_string(MAX_NOTAS) + ": ");
            std::replace(texto.begin(), texto.end(), ',', '.');
            const auto valor = a_numero(texto);
            if (!valor) {
                std::cout << MSG_NUMERICO << "\n";
                continue;
            }
            if (*valor <= 0) {
                std::cout << "Error: La nota debe ser mayor que 0.\n";
                continue;
            }
            if (*valor < 1 || *valor > 20) {
                std::cout << "Error: La nota debe estar entre 1 y 20.\n";
                continue;
            }
            nota = *valor;
            break;
        }
        alumno->agregar_nota(nota);
        guardar_alumnos(alumnos);  // se guarda nota por nota
        pila.push(alumno->cedula());
    }
    std::cout << "Notas registradas para " << alumno->nombre() << ": "
              << unir_notas(alumno->notas(), ", ") << ".\n";
}

void deshacer_nota(std::vector<Alumno>& alumnos,
                   std::stack<std::string>& pila) {
    if (pila.empty()) {
        std::cout << "No hay registros de notas para deshacer.\n";
        return;
    }
    const std::string cedula = pila.top();  // LIFO
    pila.pop();
    Alumno* alumno = buscar_alumno(alumnos, cedula);
    if (!alumno) {
        std::cout << "Error: El alumno de ese registro ya no existe.\n";
        return;
    }
    const auto nota = alumno->quitar_ultima_nota();
    if (!nota) {
        std::cout << "No hay registros de notas para deshacer.\n";
        return;
    }
    guardar_alumnos(alumnos);
    std::cout << "Se deshizo la nota " << fmt_g(*nota) << " de "
              << alumno->nombre() << ".\n";
}

void generar_cola_certificados() {
    const auto alumnos = cargar_alumnos();  // se analiza lo que hay en el archivo
    std::queue<const Alumno*> cola;
    for (const auto& a : alumnos)
        if (a.aprobado()) cola.push(&a);

    const std::string barra(41, '=');
    std::vector<std::string> lineas = {
        barra,
        "REPORTE DE CERTIFICADOS PENDIENTES",
        barra,
        "Total de graduandos en cola: " + std::to_string(cola.size()),
        "",
    };
    int posicion = 1;
    while (!cola.empty()) {
        const Alumno* a = cola.front();  // FIFO
        cola.pop();
        lineas.push_back(std::to_string(posicion) + ". [" + a->cedula() +
                         "] " + a->nombre());
        lineas.push_back("   - Programa: " + a->programa().nombre());
        lineas.push_back("   - Promedio Final: " + fmt_prom(a->promedio()));
        lineas.push_back("   - Estatus: APROBADO (" +
                         a->programa().descripcion_regla() + ")");
        lineas.push_back("");
        ++posicion;
    }
    lineas.push_back(barra);
    lineas.push_back("* Fin del reporte - Generado por SGA-DO *");
    escribir_atomico(ARCHIVO_CERTIFICADOS, lineas);
    std::cout << "Cola procesada: " << posicion - 1
              << " graduando(s) exportado(s) a certificados_pendientes.txt.\n";
}

void mostrar_reporte() {
    const auto profesores = cargar_profesores();
    const auto alumnos = cargar_alumnos();

    std::cout << "\n--- PROFESORES ACTIVOS ---\n";
    if (profesores.empty()) std::cout << "(sin profesores registrados)\n";
    for (const auto& p : profesores) {
        std::cout << "[" << p.cedula() << "] " << p.nombre() << " | "
                  << p.correo() << " | Especialidad: " << p.especialidad()
                  << " | Materia: " << p.materia() << "\n";
    }

    std::cout << "\n--- ALUMNOS REGISTRADOS ---\n";
    if (alumnos.empty()) std::cout << "(sin alumnos registrados)\n";
    for (const auto& a : alumnos) {
        const std::string notas =
            a.notas().empty() ? "sin notas" : unir_notas(a.notas(), ", ");
        const std::string prom =
            a.notas().empty() ? "-" : fmt_prom(a.promedio());
        std::cout << "[" << a.cedula() << "] " << a.nombre() << " | "
                  << a.programa().nombre() << " | Notas: " << notas
                  << " | Promedio: " << prom
                  << " | Estatus: " << a.estatus() << "\n";
    }
}

// ---------------------------------------------------------------------------
// Menu principal
// ---------------------------------------------------------------------------

const char* MENU = R"(
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
==================================================)";

// Devuelve nullopt si el texto no es un numero entero
std::optional<long long> a_entero(const std::string& texto) {
    try {
        std::size_t usados = 0;
        const long long valor = std::stoll(texto, &usados);
        if (usados != texto.size()) return std::nullopt;
        return valor;
    } catch (const std::logic_error&) {
        return std::nullopt;
    }
}

int main(int argc, char* argv[]) {
    try {
        // Los .txt se guardan en la carpeta del ejecutable
        if (argc > 0) {
            const fs::path base = fs::absolute(argv[0]).parent_path();
            ARCHIVO_ALUMNOS = (base / ARCHIVO_ALUMNOS).string();
            ARCHIVO_PROFESORES = (base / ARCHIVO_PROFESORES).string();
            ARCHIVO_CERTIFICADOS = (base / ARCHIVO_CERTIFICADOS).string();
        }

        auto alumnos = cargar_alumnos();
        auto profesores = cargar_profesores();
        std::stack<std::string> pila_notas;  // Pila LIFO para deshacer

        while (true) {
            std::cout << MENU << "\n";
            try {
                const auto opcion = a_entero(
                    recortar(leer_linea("Seleccione una opcion (1-7): ")));
                if (!opcion) {
                    std::cout << MSG_NUMERICO << "\n";
                    continue;
                }

                if (*opcion == 1) {
                    registrar_alumno(alumnos);
                } else if (*opcion == 2) {
                    registrar_profesor(profesores);
                } else if (*opcion == 3) {
                    registrar_nota(alumnos, pila_notas);
                } else if (*opcion == 4) {
                    deshacer_nota(alumnos, pila_notas);
                } else if (*opcion == 5) {
                    generar_cola_certificados();
                } else if (*opcion == 6) {
                    mostrar_reporte();
                } else if (*opcion == 7) {
                    guardar_alumnos(alumnos);
                    guardar_profesores(profesores);
                    std::cout << "Datos guardados. Hasta pronto.\n";
                    break;
                } else {
                    std::cout << "Error: Seleccione una opcion entre 1 y 7.\n";
                }
            } catch (const std::runtime_error& e) {
                
                std::cout << "Error de archivo: " << e.what() << "\n";
            }
        }
    } catch (const FinDeEntrada&) {
        std::cout << "\nEntrada finalizada. Cerrando de forma segura.\n";
    } catch (const std::exception& e) {
        std::cerr << "Error fatal: " << e.what() << "\n";
        return 1;
    }
    return 0;
}
