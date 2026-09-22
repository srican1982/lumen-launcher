const nav = document.querySelector("[data-nav]");
const toggle = document.querySelector("[data-nav-toggle]");
const menu = document.querySelector("#menu");
const year = document.querySelector("[data-year]");

if (year) year.textContent = String(new Date().getFullYear());

const setClock = () => {
  document.querySelectorAll("[data-clock]").forEach((node) => {
    node.textContent = new Date().toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });
  });
};
setClock();
setInterval(setClock, 30_000);

toggle?.addEventListener("click", () => {
  const open = nav.classList.toggle("is-open");
  toggle.setAttribute("aria-expanded", String(open));
});

menu?.querySelectorAll("a").forEach((link) => {
  link.addEventListener("click", () => {
    nav.classList.remove("is-open");
    toggle?.setAttribute("aria-expanded", "false");
  });
});

const cycle = [
  { wall: "wall-home", greet: "Good morning", space: "Home" },
  { wall: "wall-work", greet: "Good afternoon", space: "Work" },
  { wall: "wall-personal", greet: "Good evening", space: "Personal" },
  { wall: "wall-focus", greet: "Good night", space: "Focus" },
  { wall: "wall-travel", greet: "Ready to go", space: "Travel" }
];
const phone = document.querySelector("[data-space-cycle]");
if (phone) {
  const screen = phone.querySelector(".screen");
  const greet = phone.querySelector("[data-cycle-greet]");
  const space = phone.querySelector("[data-cycle-space]");
  if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
  let i = 0;
  setInterval(() => {
    i = (i + 1) % cycle.length;
    screen.className = `screen ${cycle[i].wall}`;
    greet.textContent = cycle[i].greet;
    space.textContent = cycle[i].space;
  }, 2800);
}
