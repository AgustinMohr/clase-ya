# ADR-031 — Evidencia sin escáner: riesgo declarado, PDF-only y estado honesto

**Estado:** Aceptado (2026-10-30)
**Relacionado:** `STOR-002`, `STOR-001`, `ADR-026`, `TEACHER-001`.

## Contexto

`STOR-001` decidió guardar la evidencia con ClamAV **antes** de persistirla (fail-closed) y
`ProductionConfigValidator` exige `SCAN_ENABLED=true` fuera de `local`/`test`. El deploy corre en
**Render Free**, que no ofrece servicios privados gratuitos ni RAM suficiente para un `clamd`
(~512 MB compartidos con el JVM), y el director decidió no pagar todavía.

Resultado hasta hoy: la subida de evidencia responde `503`, y mientras tanto el código escribía
`scan_status = CLEAN` incluso con el escáner no-op, es decir, registraba un escaneo que nunca
ocurrió.

## Decisión

1. **Solo PDF**, validado por magic bytes. Se eliminan los parsers de JPG/PNG del servidor y del
   navegador del administrador.
2. **Estado honesto.** Se agrega `UNSCANNED` a `scan_status` (migración `V15`). El `DocumentScanner`
   declara el estado a registrar: `CLEAN` cuando escaneó, `UNSCANNED` cuando está deshabilitado. El
   servicio deja de hardcodear `CLEAN`.
3. **Ack explícito en vez de guard eliminado.** Fuera de `local`/`test`, `SCAN_ENABLED=false` solo
   arranca con `ALLOW_UNSCANNED_EVIDENCE=true`. El invariante sigue vivo: evidencia sin escanear
   requiere una decisión escrita en el entorno, no un olvido.
4. **Descarga endurecida.** `attachment` (ya existía), `nosniff` (ya existía a nivel global),
   `Cache-Control: no-store` (nuevo) y filename restringido a un charset seguro. Nunca render inline.
5. **Sin visor en la app** por ahora: renderizar un PDF no confiable en el navegador es lo que el
   `attachment` evita. Un visor seguro exige rasterización en servidor o PDF.js sandboxeado + CSP.

## Consecuencias

- **Positivas:** la verificación documental funciona en el deploy gratuito; los datos dicen la
  verdad sobre el escaneo; el riesgo queda documentado y es reversible (cuando haya ClamAV, se pone
  `SCAN_ENABLED=true` y el ack deja de usarse).
- **Riesgo residual aceptado:** sin antivirus, un PDF malicioso puede afectar a quien lo abra. Se
  mitiga con acceso solo ADMIN, descarga forzada (el archivo se escanea con el antivirus del SO al
  aterrizar), `no-store`, log de auditoría de descargas y la revisión manual del operador.
- **Costos:** la evidencia no se puede mostrar dentro de la app; revisar requiere descargar. Si más
  adelante se quiere visor, es otra spec con su dependencia.
- **Alternativas descartadas:** pagar ClamAV (sin presupuesto por ahora); desactivar la verificación
  (el director la quiere operativa para clientes reales); API de antivirus externa (sube documentos
  de identidad a un tercero).
