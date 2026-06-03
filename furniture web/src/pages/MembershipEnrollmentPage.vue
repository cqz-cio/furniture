<script setup>
import { membershipRoutes } from "../services/membershipNavigation.js";

const plans = [
  {
    title: "Annual Membership",
    price: "$200 / year",
    description: "For individual customers who want account-based member savings and annual renewal management.",
  },
  {
    title: "Whole-Room Membership",
    price: "Project based",
    description: "For room-level furnishing projects that need whole-room benefits and service visibility.",
  },
];
</script>

<template>
  <section class="membership-page membership-narrow">
    <header class="membership-page-head">
      <p class="eyebrow">Membership Enrollment</p>
      <h1>Select a membership plan.</h1>
      <p>Membership is treated as a purchasable service before checkout, with account sign-in required.</p>
    </header>

    <section class="membership-plan-list" aria-label="Membership plans">
      <article v-for="plan in plans" :key="plan.title">
        <div>
          <h2>{{ plan.title }}</h2>
          <p>{{ plan.description }}</p>
        </div>
        <strong>{{ plan.price }}</strong>
      </article>
    </section>

    <section class="membership-agreement">
      <label>
        <input type="checkbox" />
        <span>I agree to the Members Program Terms &amp; Conditions.</span>
      </label>
      <a :href="membershipRoutes.membershipTerms">Read Terms</a>
    </section>
  </section>
</template>
