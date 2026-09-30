# ClaseYa — suite E2E (Playwright)

Levanta el stack completo (Spring Boot con perfil `local` + Vite) y corre los flujos críticos
contra la **base demo local**. Requiere Docker Desktop activo y la base `claseya-pg`.

Uso:

```powershell
scripts\e2e.ps1                  # levanta su PROPIO backend (determinista)
scripts\e2e.ps1 -ReuseBackend    # reusa el backend ya levantado (rápido, pero solo vale si
                                 # tiene el código actual compilado)
```

Detalle importante: por defecto la suite **no reusa** un backend ya levantado. Reusar uno viejo
es exactamente lo que hizo que la UI pareciera rota mientras el código estaba bien.
