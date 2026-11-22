
import math
import os
from abc import ABC, abstractmethod
from collections import deque

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
ARCHIVO_ALUMNOS = os.path.join(BASE_DIR, "alumnos.txt")
ARCHIVO_PROFESORES = os.path.join(BASE_DIR, "profesores.txt")
ARCHIVO_CERTIFICADOS = os.path.join(BASE_DIR, "certificados_pendientes.txt")

MAX_NOTAS = 3
MSG_NUMERICO = "Error: Ingrese un valor numerico valido"


# Asignamos la clase de programa academico con sus atributos

class ProgramaAcademico(ABC):
    nombre = "Programa"

    @staticmethod
    def promedio(notas):
        return sum(notas) / len(notas) if notas else 0.0

    @abstractmethod
    def evaluar_aprobacion(self, notas):
        """Devuelve True si las notas aprueban segun la regla del programa."""

    @abstractmethod
    def descripcion_regla(self):
        """Texto que explica por que se aprueba (para reportes)."""


class Curso(ProgramaAcademico):
    nombre = "Curso"

    def evaluar_aprobacion(self, notas):
        return self.promedio(notas) >= 10

    def descripcion_regla(self):
        return "Cumple promedio >= 10"


class Diplomado(ProgramaAcademico):
    nombre = "Diplomado"

    def evaluar_aprobacion(self, notas):
        return self.promedio(notas) >= 14

    def descripcion_regla(self):
        return "Cumple promedio >= 14"


class Bootcamp(ProgramaAcademico):
    nombre = "Bootcamp"

    def evaluar_aprobacion(self, notas):
        return all(n >= 14 for n in notas)

    def descripcion_regla(self):
        return "Cumple regla de ninguna nota < 14"


def fmt_prom(valor):
    """Promedio truncado a 1 decimal (13.67 -> 13.6), como en el PDF."""
    return f"{math.floor(valor * 10 + 1e-9) / 10:.1f}"


PROGRAMAS = {"curso": Curso, "diplomado": Diplomado, "bootcamp": Bootcamp}


def crear_programa(tipo):
    clase = PROGRAMAS.get(tipo.strip().lower())
    return clase() if clase else None



# Asignamos la super clase Persona y las subclases Alumno y Profesor con sus atributos 

class Persona:
    def __init__(self, cedula, nombre, correo):
        self._cedula = cedula
        self._nombre = nombre
        self._correo = correo

    @property
    def cedula(self):
        return self._cedula

    @property
    def nombre(self):
        return self._nombre

    @property
    def correo(self):
        return self._correo


class Alumno(Persona):
    def __init__(self, cedula, nombre, correo, programa, notas=None):
        super().__init__(cedula, nombre, correo)
        self._programa = programa
        self._notas = list(notas) if notas else []

    @property
    def programa(self):
        return self._programa

    @property
    def notas(self):
        return list(self._notas)

    def agregar_nota(self, nota):
        if len(self._notas) >= MAX_NOTAS:
            raise ValueError(
                f"El alumno ya tiene las {MAX_NOTAS} notas permitidas.")
        self._notas.append(nota)

    def quitar_ultima_nota(self):
        return self._notas.pop() if self._notas else None

    def tiene_notas_completas(self):
        return len(self._notas) >= MAX_NOTAS

    def promedio(self):
        return self._programa.promedio(self._notas)

    def aprobado(self):
        return self.tiene_notas_completas() and \
            self._programa.evaluar_aprobacion(self._notas)

    def estatus(self):
        if not self.tiene_notas_completas():
            return "PENDIENTE (notas incompletas)"
        return "APROBADO" if self.aprobado() else "REPROBADO"

    def a_linea(self):
        notas = self._notas + [0] * (MAX_NOTAS - len(self._notas))
        notas_txt = ",".join(f"{n:g}" for n in notas)
        return (f"{self._cedula},{self._nombre},{self._correo},"
                f"{self._programa.nombre},{notas_txt}")


class Profesor(Persona):
    def __init__(self, cedula, nombre, correo, especialidad, materia):
        super().__init__(cedula, nombre, correo)
        self._especialidad = especialidad
        self._materia = materia

    @property
    def especialidad(self):
        return self._especialidad

    @property
    def materia(self):
        return self._materia

    def a_linea(self):
        return (f"{self._cedula},{self._nombre},{self._correo},"
                f"{self._especialidad},{self._materia}")

# Funciones de carga y guardado de datos

def _escribir_atomico(ruta, lineas):
    """Escribe a un temporal y lo reemplaza: evita archivos corruptos
    si el programa se cierra a mitad de la escritura."""
    tmp = ruta + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        for linea in lineas:
            f.write(linea + "\n")
        f.flush()
        os.fsync(f.fileno())
    os.replace(tmp, ruta)


def guardar_alumnos(alumnos):
    _escribir_atomico(ARCHIVO_ALUMNOS, [a.a_linea() for a in alumnos])


def guardar_profesores(profesores):
    _escribir_atomico(ARCHIVO_PROFESORES, [p.a_linea() for p in profesores])


def cargar_alumnos():
    alumnos = []
    if not os.path.exists(ARCHIVO_ALUMNOS):
        return alumnos
    with open(ARCHIVO_ALUMNOS, encoding="utf-8") as f:
        for n, linea in enumerate(f, 1):
            linea = linea.strip()
            if not linea:
                continue
            try:
                partes = [p.strip() for p in linea.split(",")]
                cedula, nombre, correo, tipo = partes[:4]
                programa = crear_programa(tipo)
                if programa is None:
                    raise ValueError(f"programa desconocido '{tipo}'")
                # Los ceros aparecen como "sin nota registrada".
                notas = [float(x) for x in partes[4:4 + MAX_NOTAS]]
                notas = [x for x in notas if x != 0]
                alumnos.append(Alumno(cedula, nombre, correo, programa, notas))
            except (ValueError, IndexError) as e:
                print(f"Aviso: linea {n} de alumnos.txt ignorada ({e}).")
    return alumnos


def cargar_profesores():
    profesores = []
    if not os.path.exists(ARCHIVO_PROFESORES):
        return profesores
    with open(ARCHIVO_PROFESORES, encoding="utf-8") as f:
        for n, linea in enumerate(f, 1):
            linea = linea.strip()
            if not linea:
                continue
            partes = [p.strip() for p in linea.split(",")]
            if len(partes) < 5:
                print(f"Aviso: linea {n} de profesores.txt ignorada.")
                continue
            profesores.append(Profesor(*partes[:5]))
    return profesores

# Aqui se ingresan los datos de los alumnos y profesores
def pedir_texto(mensaje):
    """Pide un texto no vacio; las comas se eliminan (separador del .txt)."""
    while True:
        valor = input(mensaje).replace(",", " ").strip()
        if valor:
            return " ".join(valor.split())
        print("Error: Este campo no puede estar vacio.")


def pedir_programa():
    while True:
        valor = input("Tipo de Programa (Curso/Diplomado/Bootcamp): ")
        programa = crear_programa(valor)
        if programa:
            return programa
        print("Error: Elija Curso, Diplomado o Bootcamp.")


def buscar_alumno(alumnos, cedula):
    for a in alumnos:
        if a.cedula.upper() == cedula.upper():
            return a
    return None

# Opciones del menu

def registrar_alumno(alumnos):
    cedula = pedir_texto("Cedula/ID: ").upper()
    if buscar_alumno(alumnos, cedula):
        print(f"Error: Ya existe un alumno con la cedula {cedula}.")
        return
    nombre = pedir_texto("Nombre completo: ")
    correo = pedir_texto("Correo electronico: ")
    programa = pedir_programa()
    alumnos.append(Alumno(cedula, nombre, correo, programa))
    guardar_alumnos(alumnos)
    print(f"Alumno {nombre} registrado y guardado en alumnos.txt.")


def registrar_profesor(profesores):
    cedula = pedir_texto("Cedula/ID: ").upper()
    if any(p.cedula.upper() == cedula for p in profesores):
        print(f"Error: Ya existe un profesor con la cedula {cedula}.")
        return
    nombre = pedir_texto("Nombre completo: ")
    correo = pedir_texto("Correo electronico: ")
    especialidad = pedir_texto("Especialidad: ")
    materia = pedir_texto("Materia asignada: ")
    profesores.append(Profesor(cedula, nombre, correo, especialidad, materia))
    guardar_profesores(profesores)
    print(f"Profesor {nombre} registrado y guardado en profesores.txt.")


def registrar_nota(alumnos, pila):
    cedula = pedir_texto("Cedula del alumno: ")
    alumno = buscar_alumno(alumnos, cedula)
    if alumno is None:
        print("Error: No se encontro un alumno con esa cedula.")
        return
    print(f"Alumno: {alumno.nombre} ({alumno.programa.nombre}) - "
          f"Notas actuales: {[f'{n:g}' for n in alumno.notas] or 'ninguna'}")
    if alumno.tiene_notas_completas():
        print(f"Error: El alumno ya tiene las {MAX_NOTAS} notas permitidas.")
        return
    # Se piden todas las notas que faltan, una tras otra, sin volver al menu.
    faltantes = MAX_NOTAS - len(alumno.notas)
    for _ in range(faltantes):
        numero = len(alumno.notas) + 1
        while True:
            texto = input(f"Nota {numero} de {MAX_NOTAS}: ")
            texto = texto.strip().replace(",", ".")
            try:
                nota = float(texto)
            except ValueError:
                print(MSG_NUMERICO)
                continue
            if nota <= 0:
                print("Error: La nota debe ser mayor que 0.")
                continue
            if nota < 1 or nota > 20:
                print("Error: La nota debe estar entre 1 y 20.")
                continue
            break
        alumno.agregar_nota(nota)
        guardar_alumnos(alumnos)  # se guarda nota por nota 
        pila.append(alumno.cedula)  
    print(f"Notas registradas para {alumno.nombre}: "
          f"{', '.join(f'{n:g}' for n in alumno.notas)}.")


def deshacer_nota(alumnos, pila):
    if not pila:
        print("No hay registros de notas para deshacer.")
        return
    cedula = pila.pop()  # LIFO
    alumno = buscar_alumno(alumnos, cedula)
    if alumno is None:
        print("Error: El alumno de ese registro ya no existe.")
        return
    nota = alumno.quitar_ultima_nota()
    guardar_alumnos(alumnos)
    print(f"Se deshizo la nota {nota:g} de {alumno.nombre}.")


def generar_cola_certificados():
    alumnos = cargar_alumnos()  # se analiza lo que hay en el archivo
    cola = deque()
    for a in alumnos:
        if a.aprobado():
            cola.append(a)  

    lineas = [
        "=" * 41,
        "REPORTE DE CERTIFICADOS PENDIENTES",
        "=" * 41,
        f"Total de graduandos en cola: {len(cola)}",
        "",
    ]
    posicion = 1
    while cola:
        a = cola.popleft()  # FIFO
        lineas += [
            f"{posicion}. [{a.cedula}] {a.nombre}",
            f"   - Programa: {a.programa.nombre}",
            f"   - Promedio Final: {fmt_prom(a.promedio())}",
            f"   - Estatus: APROBADO ({a.programa.descripcion_regla()})",
            "",
        ]
        posicion += 1
    lineas += ["=" * 41, "* Fin del reporte - Generado por SGA-DO *"]
    _escribir_atomico(ARCHIVO_CERTIFICADOS, lineas)
    print(f"Cola procesada: {posicion - 1} graduando(s) exportado(s) a "
          f"certificados_pendientes.txt.")


def mostrar_reporte():
    profesores = cargar_profesores()
    alumnos = cargar_alumnos()
    print("\n--- PROFESORES ACTIVOS ---")
    if not profesores:
        print("(sin profesores registrados)")
    for p in profesores:
        print(f"[{p.cedula}] {p.nombre} | {p.correo} | "
              f"Especialidad: {p.especialidad} | Materia: {p.materia}")
    print("\n--- ALUMNOS REGISTRADOS ---")
    if not alumnos:
        print("(sin alumnos registrados)")
    for a in alumnos:
        notas = ", ".join(f"{n:g}" for n in a.notas) or "sin notas"
        prom = fmt_prom(a.promedio()) if a.notas else "-"
        print(f"[{a.cedula}] {a.nombre} | {a.programa.nombre} | "
              f"Notas: {notas} | Promedio: {prom} | Estatus: {a.estatus()}")



# Mnu principal

MENU = """
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
=================================================="""


def main():
    alumnos = cargar_alumnos()
    profesores = cargar_profesores()
    pila_notas = []  # Pila LIFO para "deshacer"

    while True:
        print(MENU)
        try:
            entrada = input("Seleccione una opcion (1-7): ").strip()
            try:
                opcion = int(entrada)
            except ValueError:
                print(MSG_NUMERICO)
                continue

            if opcion == 1:
                registrar_alumno(alumnos)
            elif opcion == 2:
                registrar_profesor(profesores)
            elif opcion == 3:
                registrar_nota(alumnos, pila_notas)
            elif opcion == 4:
                deshacer_nota(alumnos, pila_notas)
            elif opcion == 5:
                generar_cola_certificados()
            elif opcion == 6:
                mostrar_reporte()
            elif opcion == 7:
                guardar_alumnos(alumnos)
                guardar_profesores(profesores)
                alumnos.clear()
                profesores.clear()
                pila_notas.clear()
                print("Datos guardados. Hasta pronto.")
                break
            else:
                print("Error: Seleccione una opcion entre 1 y 7.")
        except (EOFError, KeyboardInterrupt):
            print("\nEntrada finalizada. Cerrando de forma segura.")
            break
        except OSError as e:
            print(f"Error de archivo: {e}")


if __name__ == "__main__":
    main()
