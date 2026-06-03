<script setup>
import { computed } from "vue";
import { getCheckoutAuthOptions } from "../services/membershipNavigation.js";

const props = defineProps({
  items: {
    type: Array,
    default: () => [],
  },
});

const emit = defineEmits(["continue-checkout"]);
const authOptions = computed(() => getCheckoutAuthOptions(props.items));

const chooseOption = (option) => {
  if (option.disabled) return;
  if (option.key === "guest") emit("continue-checkout");
};
</script>

<template>
  <section class="membership-page membership-narrow">
    <header class="membership-page-head">
      <p class="eyebrow">Checkout</p>
      <h1>Sign in, create an account or continue as guest.</h1>
      <p>Membership purchases and member pricing require an account before checkout continues.</p>
    </header>

    <section class="checkout-auth-options" aria-label="Checkout authentication choices">
      <article v-for="option in authOptions" :key="option.key" :class="{ disabled: option.disabled }">
        <h2>{{ option.title }}</h2>
        <p>{{ option.description }}</p>
        <small v-if="option.disabled">{{ option.reason }}</small>
        <small v-else-if="option.disabledForMembership">Membership services require account checkout.</small>
        <button v-if="option.key === 'guest'" type="button" :disabled="option.disabled" @click="chooseOption(option)">
          {{ option.cta }}
        </button>
        <a v-else :href="option.href" @click="chooseOption(option)">{{ option.cta }}</a>
      </article>
    </section>
  </section>
</template>
