module.exports = {
  extends: ["@commitlint/config-conventional"],
  rules: {
    "type-enum": [
      2,
      "always",
      ["feat", "fix", "docs", "chore", "build", "test", "refactor", "ci", "perf"],
    ],
    "header-max-length": [2, "always", 100],
  },
};
