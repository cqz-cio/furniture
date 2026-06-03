<script setup>
import { checkoutAuthOptions } from "../services/membershipNavigation.js";
</script>

<template>
  <section class="membership-page membership-narrow">
    <header class="membership-page-head">
      <p class="eyebrow">Checkout</p>
      <h1>Sign in, create an account or continue as guest.</h1>
      <p>Membership purchases and member pricing require an account before checkout continues.</p>
    </header>

    <section class="checkout-auth-options" aria-label="Checkout authentication choices">
      <article v-for="option in checkoutAuthOptions" :key="option.key">
        <h2>{{ option.title }}</h2>
        <p>{{ option.description }}</p>
        <small v-if="option.disabledForMembership">Not available when the bag contains a membership service.</small>
        <a :href="option.href">{{ option.cta }}</a>
      </article>
    </section>
  </section>
</template>
