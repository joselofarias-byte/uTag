# Mascotas y personas — revisión del 23/10/2026

Base revisada: `fc59eb75cfee61694812805922673598fbbad5d6`, integración de los PR #1–#8 del fork. Esta rama conserva esas funciones y añade correcciones; no instala un segundo grabador.

## Mascotas: existente y mejoras de esta propuesta

El modo experimental existente registra teléfono y última posición del SmartTag por separado, tiene eventos, historial y mapa. No convierte al SmartTag en un GPS autónomo.

Correcciones:

- El teléfono debe aportar una posición nueva, no simulada, de hasta 30 s y precisión de hasta 50 m. Ya no se acepta inmediatamente cualquier ubicación en caché.
- Se propaga la cancelación de coroutines: detener el trabajo no se transforma en un error silencioso seguido de más consultas.
- La pérdida de todas las posiciones queda registrada como una ruptura, en lugar de desaparecer de la serie.
- Mapa y distancia comparten la misma segmentación: posiciones ausentes/invalidas, tiempo retrocediendo y huecos mayores a 3 minutos separan recorridos. No se dibuja ni suma una línea que atraviese esos huecos.
- Se deduplican posiciones idénticas con la misma fecha del SmartTag; consultar de nuevo una posición vieja no la convierte en una posición nueva.
- Exportación GPX desde el detalle del paseo mediante el selector de archivos. Dos tracks: teléfono y red SmartTag, cada uno con sus fechas originales y sus segmentos. No incluye identificadores de cuenta/tag ni eventos/notas en esta primera versión. Los eventos siguen disponibles en el historial de la aplicación.
- El GPX incluye el nombre de la mascota y coordenadas. Compartirlo es una acción explícita del usuario; no se transmite automáticamente a ningún servidor adicional.

Limitaciones conservadas del grabador existente: el almacenamiento de paseos es Room privado sin cifrado adicional; no se afirma que tenga el cifrado experimental de una propuesta anterior. No se ha validado en HONOR/SmartTag. El filtrado no elimina todo el ruido GPS ni reconstruye movimientos de la mascota entre observaciones de Samsung.

## Personas

El catálogo existente lista miembros devueltos por `/devices`. Su UI ya explica que el seguimiento de personas en vivo no está implementado. Esto no es todavía una alternativa funcional a compartir ubicación entre familiares.

La rama pública original contiene `TAB_PEOPLE` en FindService, pero no operaciones verificadas para listar comparticiones, consultar posición, invitar, aceptar, caducar ni revocar. No se inventaron endpoints ni se habilitó seguimiento por pertenecer al mismo hogar.

Siguiente requisito concreto: obtener el contrato público o inspeccionar, con cuentas de prueba autorizadas, una compartición Samsung Find aceptada por ambos participantes. Se necesitan respuestas redactadas de posición/fecha, permisos, cifrado y revocación. Después se podrá conectar el catálogo a ubicaciones reales con actualización y estados de última conexión. Una alternativa es un servicio propio entre teléfonos, pero requiere servidor y cliente en cada equipo, y no equivale a Samsung Find.

Referencia: https://github.com/KieronQuinn/uTag/issues/2

## Versiones

La integración reciente optó por mantener los metadatos del mod en 1.0.19/1019 para reflejar el módulo del APK publicado. Se conserva esa decisión: no se cambia a 1020 sin construir un APK nuevo. Se añade `scripts/check_mod_version.py` para contrastar JSON y módulo incrustado, y un test que impide ofrecer una versión remota menor como actualización.

## Validación

`python scripts/test_pet_core.py` compila las clases puras con Kotlin 2.1.0 y ejecuta JUnit: política de versiones, geometría y GPX. Las dependencias están fijadas por SHA-256. No necesita Android, firma ni bibliotecas Samsung.

El workflow también intenta compilación Android con JDK 21. Excluye FMM y Google Services y usa clave Maps ficticia sólo para verificar código/recursos: no empaqueta ni certifica un APK funcional. La compilación local completa no pudo iniciarse por la descarga de Gradle; consultar el resultado real del workflow antes de fusionar.

Prueba física pendiente: iniciar un paseo al aire libre, bloquear pantalla, verificar continuidad en MagicOS, perder/recuperar GPS, detener durante una consulta, exportar GPX y cotejar teléfono/SmartTag/fechas/tramos con el historial. Probar cancelación del selector y rotación durante exportación: si se pierde la instantánea se pide exportar de nuevo, sin informar éxito falso.
