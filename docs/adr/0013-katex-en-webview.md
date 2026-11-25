---
status: aceptado
date: 2026-11-06
decision-makers: Responsable del proyecto
---

# Fórmulas con KaTeX dentro de un WebView

## Contexto y problema

El contenido usa LaTeX avanzado: `\begin{array}`, `\underbrace`, `\textcolor` y `\textbf`. Enseñar mal una fórmula es un error pedagógico.

## Factores de la decisión

* Dibujar igual que la web.
* Funcionar sin Internet.
* Que el tema no se sienta lento.

## Opciones consideradas

* KaTeX y marked dentro de un WebView, con sus archivos en `assets/web/`
* Una librería nativa de LaTeX para Compose

## Decisión

Opción elegida: "KaTeX en WebView". Un texto sin `$` usa `Text` de Compose.

### Consecuencias

* Bueno: las fórmulas se ven como en la web, sin red.
* Malo: el WebView pesa más que un `Text`; por eso solo se usa cuando hay fórmula.

### Confirmación

Prueba en la MatePad (fase 1): la lección, los enunciados y las opciones se ven como en la web.

## Más información

`PLAN-MIGRACION.md`, sección 5, "Fórmulas".
