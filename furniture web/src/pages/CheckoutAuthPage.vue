<script setup>
import { computed } from "vue";
import { getCheckoutAuthOptions } from "../services/membershipNavigation.js";
import { useI18n } from "../i18n.js";

const props = defineProps({
  items: {
    type: Array,
    default: () => [],
  },
});

const emit = defineEmits(["continue-checkout"]);
const { t } = useI18n();
const authOptions = computed(() => getCheckoutAuthOptions(props.items));

const chooseOption = (option) => {
  if (option.disabled) return;
  if (option.key === "guest") emit("continue-checkout");
};
</script>

<template>
  <section class="membership-page membership-narrow service-page-shell">
    <header class="membership-page-head">
      <p class="eyebrow">{{ t("membership.checkoutAuth.eyebrow") }}</p>
      <h1>{{ t("membership.checkoutAuth.title") }}</h1>
      <p>{{ t("membership.checkoutAuth.intro") }}</p>
    </header>

    <section class="checkout-auth-options" :aria-label="t('membership.checkoutAuth.aria')">
      <article v-for="option in authOptions" :key="option.key" :class="{ disabled: option.disabled }">
        <h2>{{ t(`membership.checkoutAuth.${option.key}.title`) }}</h2>
        <p>{{ t(`membership.checkoutAuth.${option.key}.description`) }}</p>
        <small v-if="option.disabled">{{ t("membership.checkoutAuth.guestDisabledReason") }}</small>
        <small v-else-if="option.disabledForMembership">{{ t("membership.checkoutAuth.guestMembershipNote") }}</small>
        <button v-if="option.key === 'guest'" type="button" :disabled="option.disabled" @click="chooseOption(option)">
          {{ t(`membership.checkoutAuth.${option.key}.cta`) }}
        </button>
        <a v-else :href="option.href" @click="chooseOption(option)">
          {{ t(`membership.checkoutAuth.${option.key}.cta`) }}
        </a>
      </article>
    </section>
  </section>
</template>
