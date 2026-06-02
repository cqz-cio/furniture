<script setup>
import { rhFooter } from "../data/rhLayout.js";
</script>

<template>
  <footer class="rh-footer" aria-label="RH footer extraction">
    <section class="footer-newsletter" aria-label="Email signup">
      <h2>{{ rhFooter.newsletter.title }}</h2>
      <p>{{ rhFooter.newsletter.subtitle }}</p>
      <form>
        <input type="email" :placeholder="rhFooter.newsletter.inputLabel" :aria-label="rhFooter.newsletter.inputLabel" />
        <button type="button">{{ rhFooter.newsletter.action }}</button>
      </form>
    </section>

    <section class="footer-links" aria-label="Footer links">
      <article v-for="column in rhFooter.columns" :key="column.title">
        <h3>{{ column.title }}</h3>
        <a v-for="link in column.links" :key="link" href="#">{{ link }}</a>
      </article>
    </section>

    <button class="footer-region" type="button">
      <span>{{ rhFooter.region }}</span>
      <span aria-hidden="true">⌄</span>
    </button>

    <p class="footer-copyright">{{ rhFooter.copyright }}</p>
  </footer>
</template>
