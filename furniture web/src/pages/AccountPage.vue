<script setup>
import { accountMenuItems } from "../services/membershipNavigation.js";
</script>

<template>
  <section class="account-page">
    <aside class="account-sidebar" aria-label="My Account navigation">
      <p class="eyebrow">My Account</p>
      <a v-for="item in accountMenuItems" :key="item.label" :href="item.href">{{ item.label }}</a>
    </aside>

    <section class="account-content">
      <p class="eyebrow">Account Dashboard</p>
      <h1>Manage orders, addresses, payment methods and membership.</h1>
      <p>
        The account dashboard provides the RH-aligned destination for membership status, rules, renewal and gift
        registry access.
      </p>
    </section>
  </section>
</template>
