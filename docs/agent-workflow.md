# AI-Arbeitsablauf: Claude Code + DeepSeek-Harness

Dieses Repository wird von Hermes koordiniert. Claude Code implementiert; DeepSeek arbeitet als unabhängiger Planungs- und Review-Harness. Beide schreiben niemals gleichzeitig in denselben Arbeitsbaum.

## Rollen

### Claude Code — Implementierung

- CLI: `claude`
- Modell: `claude-fable-5`
- Lädt automatisch `CLAUDE.md`.
- Darf nach konkretem Auftrag lesen, ändern und Tests ausführen.
- Arbeitet nur auf Branch `gerfried`.
- Commit/Push nur, wenn der konkrete Auftrag das ausdrücklich verlangt.

Für einen begrenzten Auftrag:

```bash
printf '%s\n' 'AUFTRAG HIER' | ./scripts/run-claude-task.sh
```

Optional kann `CLAUDE_MAX_TURNS` gesetzt werden; Standard ist 30.

### DeepSeek-Harness — Planung und Review

Hermes routet `delegate_task` über:

- Provider: `deepseek`
- Modell: `deepseek-flash`
- Bis zu sechs parallele Kinder

DeepSeek arbeitet standardmäßig read-only. Sinnvolle parallele Linsen:

1. **Produkt/Spec:** Akzeptanzkriterien, fehlende Zustände, UX-Regressionsrisiken.
2. **Backend/Sicherheit:** Auth, Autorisierung, SQLite-Migrationen, Kryptografie-/Threat-Model-Aussagen.
3. **Android/Runtime:** Compose-Zustand, Coroutines, Session-Isolation, Fehlerbehandlung.
4. **Tests/Regression:** fehlende Grenzfälle und Aussagekraft der Tests.

Jeder Befund braucht Datei, Stelle, reproduzierbares Szenario und Schweregrad. Hermes prüft die Befunde am echten Code, bevor Claude einen Fix-Auftrag erhält.

## Standardzyklus

1. Hermes erfasst den Nutzerwunsch und den sauberen Git-Ausgangspunkt.
2. Bei nichttrivialen Änderungen prüfen 2–4 DeepSeek-Linsen den betroffenen Bereich read-only.
3. Hermes konsolidiert nur belegte Befunde zu einem begrenzten Claude-Auftrag.
4. Claude implementiert eine vertikale Scheibe und führt gezielte Tests aus.
5. Hermes führt `./scripts/verify-agent-change.sh` selbst aus.
6. DeepSeek prüft den fertigen Diff erneut (Spec/Sicherheit/Runtime/Tests).
7. Claude behebt bestätigte Befunde; Hermes wiederholt die vollständigen Gates.
8. Erst danach: Commit/Push und APK-Auslieferung, wenn vom Auftrag umfasst.

## Kollisions- und Sicherheitsregeln

- Nur ein schreibender Agent zur selben Zeit.
- DeepSeek-Reviews dürfen keine Dateien ändern.
- Keine parallelen Claude-Sessions im selben Checkout.
- Vor jedem Schreibauftrag: Branch und `git status` prüfen.
- Fremde, bereits vorhandene Änderungen nicht überschreiben oder zurücksetzen.
- Keine Geheimnisse in Prompts, Logs, Commits oder Review-Ausgaben.
- Ein Agentenbericht gilt nicht als Verifikation; Hermes führt Tests und Deployment-Smokes selbst aus.
