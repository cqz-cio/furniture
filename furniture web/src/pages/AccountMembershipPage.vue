<script setup>
import { accountMenuItems, membershipRoutes } from "../services/membershipNavigation.js";

const statusRows = [
  ["Status", "Not a Member"],
  ["Plan", "Annual or Whole-Room"],
  ["Renewal", "Managed after enrollment"],
  ["Growth", "Visible after sign in"],
];
</script>

<template>
  <section class="account-page">
    <aside class="account-sidebar" aria-label="My Account navigation">
      <p class="eyebrow">My Account</p>
      <a v-for="item in accountMenuItems" :key="item.label" :href="item.href">{{ item.label }}</a>
    </aside>

    <section class="account-content">
      <p class="eyebrow">Membership</p>
      <h1>Membership status and rules live here.</h1>
      <p>
        This area manages member status, email matching, renewal controls, benefit snapshots and the member growth
        system after sign in.
      </p>

      <dl class="membership-status-list">
        <div v-for="[label, value] in statusRows" :key="label">
          <dt>{{ label }}</dt>
          <dd>{{ value }}</dd>
        </div>
      </dl>

      <div class="membership-actions">
        <a class="membership-primary-link" :href="membershipRoutes.membershipEnrollment">Join or Renew</a>
        <a :href="membershipRoutes.membershipTerms">View Rules</a>
      </div>
    </section>
  </section>
</template>
