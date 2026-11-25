// Conventional Commits con el tipo en inglés y la descripción en español (ver CONTRIBUTING.md).
export default {
  extends: ["@commitlint/config-conventional"],
  rules: {
    "type-enum": [
      2,
      "always",
      ["feat", "fix", "perf", "refactor", "test", "docs", "build", "ci", "chore", "content", "revert"],
    ],
    "scope-enum": [
      2,
      "always",
      ["dominio", "datos", "tutor", "sincro", "app", "contenido", "reglas", "herramientas", "deps", "docs", "ci", "release"],
    ],
    // Español: se permiten nombres propios y siglas (Room, Firestore, KaTeX) en la descripción.
    "subject-case": [0],
    "header-max-length": [2, "always", 100],
    // Los cuerpos pueden llevar rutas o enlaces largos.
    "body-max-line-length": [0],
    "footer-max-line-length": [0],
  },
};
