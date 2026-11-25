// The ES module that Cloudflare runs: it hands every request to the Kotlin code.
// ./gradlew :service:build writes the imported file; Kotlin/JS's default module kind is UMD.
import { org } from './build/compileSync/js/main/productionExecutable/kotlin/slippy.js';

export default {
  async fetch(request, env, ctx) {
    return org.example.slippy.handleRequest(request, env, ctx);
  }
};
