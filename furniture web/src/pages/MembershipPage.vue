<script setup>
import { getMembershipJoinTarget, membershipRoutes } from "../services/membershipNavigation.js";

const joinHref = getMembershipJoinTarget({ signedIn: false, memberStatus: "guest" });

const benefits = [
  "Member savings on eligible full-price and sale merchandise",
  "Annual membership benefits and renewal management",
  "Whole-room membership planning for larger furnishing projects",
  "Account-based access to rules, member status and growth progress",
];
</script>

<template>
  <section class="membership-page service-page-shell">
    <header class="membership-hero">
      <p class="eyebrow">Members Program</p>
      <h1>Membership, planned like a core account service.</h1>
      <p>
        Join to access member savings, account-based benefits and whole-room services aligned with the furniture
        purchase journey.
      </p>
      <div class="membership-actions service-link-row">
        <a class="membership-primary-link" :href="joinHref">Join Now</a>
        <a :href="membershipRoutes.membershipTerms">See Terms</a>
      </div>
    </header>

    <section class="membership-section membership-benefits" aria-label="Membership benefits">
      <article v-for="benefit in benefits" :key="benefit">
        <span></span>
        <p>{{ benefit }}</p>
      </article>
    </section>

    <section class="membership-section membership-two-column">
      <div>
        <p class="eyebrow">Member Growth</p>
        <h2>Growth belongs inside My Account.</h2>
      </div>
      <p>
        The public page introduces the program. Detailed levels, points, renewal status and binding rules are managed
        after sign in under Account Membership.
      </p>
      <a :href="membershipRoutes.accountMembership">View Account Membership</a>
    </section>
  </section>
</template>
