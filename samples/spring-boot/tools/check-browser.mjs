import {readFileSync} from "node:fs";
import {fileURLToPath} from "node:url";
import vm from "node:vm";
import assert from "node:assert/strict";
const source = readFileSync(fileURLToPath(new URL("../src/main/resources/static/assets/checkout.js", import.meta.url)), "utf8");
const token = '\"/><script>fixture</script>';
let config; let mounted;
const target = {dataset: {token}};
const error = {hidden: true};
await vm.runInNewContext(source, {
  document: {getElementById(id) {return id === "lunixi-checkout-form" ? target : error;}},
  window: {Lunixi: {checkout: {async create(value) {config = value; return {mount(selector) {mounted = selector;}};}}}}
});
assert.equal(config.type, "payment");
assert.equal(config.target, "lunixi-checkout-form");
assert.equal(config.options.token, token);
assert.equal(mounted, "#lunixi-checkout-form");
assert.equal(error.hidden, true);
console.log("Actual embedded asset: object/schema/token/mount contract passed (5 checks)");
