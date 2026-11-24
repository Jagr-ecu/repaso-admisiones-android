---
status: aceptado
date: 2026-11-24
decision-makers: Responsable del proyecto
---

# Registrar las decisiones de arquitectura con ADR en formato MADR

## Contexto y problema

Las decisiones del proyecto están repartidas en `PLAN-MIGRACION.md` (un diario de avances) y en `CLAUDE.md`.
Cuesta saber qué se decidió, cuándo y qué se descartó.

## Factores de la decisión

* Encontrar rápido el porqué de cada decisión.
* Formato en Markdown, revisable en un PR.
* Poco trabajo para un único desarrollador.

## Opciones consideradas

* ADR en formato MADR dentro de `docs/adr/`
* Seguir solo con `PLAN-MIGRACION.md`
* Wiki de GitHub

## Decisión

Opción elegida: "ADR en formato MADR", porque viven junto al código, pasan por PR y tienen una estructura fija.

### Consecuencias

* Bueno: cada decisión tiene contexto, opciones y consecuencias.
* Bueno: un cambio de decisión deja rastro (estado "reemplazado por").
* Malo: hay que mantenerlos; `PLAN-MIGRACION.md` sigue como diario de avances.

### Confirmación

Todo PR que cambie una decisión incluye un ADR nuevo o actualiza el estado de uno existente.

## Más información

* [MADR](https://adr.github.io/madr/)
* Plantilla: [`plantilla.md`](plantilla.md)
