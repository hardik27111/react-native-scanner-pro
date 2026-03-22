/**
 * Main entry point for React Native
 * Re-exports TypeScript module
 */

// For development/local usage, Metro bundler will handle TypeScript
// For production, this would point to compiled JavaScript
try {
  module.exports = require('./src/index.ts');
} catch (e) {
  // Fallback for environments that don't support direct TS imports
  module.exports = require('./src/index');
}

