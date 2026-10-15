(() => {
  const ano = document.getElementById("ano");
  if (ano) {
    ano.textContent = String(new Date().getFullYear());
  }

  const topo = document.querySelector(".topo");
  if (topo) {
    const atualizar = () => topo.classList.toggle("solido", window.scrollY > 24);
    atualizar();
    window.addEventListener("scroll", atualizar, { passive: true });
  }

  const features = document.querySelectorAll(".feature");
  if (!features.length || !("IntersectionObserver" in window)) {
    features.forEach((el) => el.classList.add("visivel"));
    return;
  }

  const observador = new IntersectionObserver(
    (entradas) => {
      entradas.forEach((entrada) => {
        if (entrada.isIntersecting) {
          entrada.target.classList.add("visivel");
          observador.unobserve(entrada.target);
        }
      });
    },
    { threshold: 0.25 }
  );

  features.forEach((el) => observador.observe(el));
})();
