"use strict";
(async function () {
  const target = document.getElementById("lunixi-checkout-form");
  try {
    const checkout = await window.Lunixi.checkout.create({
      type: "payment",
      target: "lunixi-checkout-form",
      options: { token: target.dataset.token }
    });
    checkout.mount("#lunixi-checkout-form");
  } catch (_) {
    document.getElementById("lunixi-error").hidden = false;
  }
})();
