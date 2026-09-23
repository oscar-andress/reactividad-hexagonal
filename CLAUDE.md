# Guía de Desarrollo y Estándares de Ingeniería

## 1. Arquitectura y Principios
- **Arquitectura:** Clean Architecture / Hexagonal (separación estricta entre Dominio, Casos de Uso/Aplicación e Infraestructura).
- **Principios:** Cumplimiento riguroso de SOLID.
  - SRP: Funciones y clases pequeñas con responsabilidad única.
  - DIP: La lógica de negocio no depende de frameworks ni bases de datos; usa interfaces/puertos.
- **Clean Code:**
  - Nombres reveladores en inglés (métodos = verbos, clases = sustantivos).
  - Uso de Early Returns (Guard Clauses) para evitar anidaciones profundas.
  - Cero valores mágicos; usa constantes o enumeraciones.
  - Manejo controlado de errores con excepciones o tipos de dominio específicos.
  - Código simple y mínimo para lograr el objetivo: evitar sobreingeniería, abstracciones prematuras o funcionalidad no solicitada.

## 2. Testing y Calidad
- Cada nuevo caso de uso o lógica de negocio debe incluir sus pruebas unitarias.
- Mocks y stubs solo en las fronteras de infraestructura (I/O, APIs externas, persistencia).
- Cubrir casos de éxito y caminos de fallo/borde.

## 3. Modo de Trabajo de Claude
- **Al crear código:** 
  1. Diseña primero las interfaces/contratos (Dominio).
  2. Implementa la lógica del caso de uso.
  3. Crea las pruebas unitarias.
  4. Agrega los adaptadores de infraestructura necesarios.
- **Al revisar código (Code Review):**
  1. Identifica violaciones a SOLID o Clean Architecture.
  2. Clasifica los hallazgos por severidad.
  3. Propón la versión refactorizada y sus tests.