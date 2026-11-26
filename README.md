# Biblia Reina Valera 1960

App Android de lectura de la Biblia, 100% local (sin internet). Texto Reina Valera 1960 completo (66 libros).

## Características
- Lista de los 66 libros con búsqueda.
- Búsqueda de palabras dentro de toda la Biblia (sin importar tildes) y de libros por nombre.
- Lectura por capítulos con botones grandes con texto (Anterior, Escuchar, Siguiente) y gestos.
- Barra inferior con pestañas: Leer, Ir a…, Buscar y Marcadores.
- Estrella para guardar un capítulo; lista de marcadores con Abrir y Borrar.
- Tamaño de letra con botones A− / A+ (o pellizcando), pensado para personas mayores.
- Ayuda corta la primera vez y desde el menú.
- Modo oscuro automático (sigue al sistema).
- El versículo del día ya no se lee solo: suena únicamente al tocar Escuchar.

## Compilación
Workflow `.github/workflows/build.yml` compila `assembleDebug` y sube el APK `biblia-kuze-debug`.

El texto está en `app/src/main/assets/biblia.json`.
