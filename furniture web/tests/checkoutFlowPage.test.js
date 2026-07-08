import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("checkout flow page planning", () => {
  it("uses the checkout flow model from CheckoutPage", () => {
    const source = readSource("../src/pages/CheckoutPage.vue");

    expect(source).toContain("buildCheckoutFlow");
    expect(source).toContain("checkoutFlow");
  });

  it("renders the RH-aligned shipping, address review, and payment sections", () => {
    const source = readSource("../src/pages/CheckoutPage.vue");

    expect(source).toContain("rh-shipping-card");
    expect(source).toContain("rh-address-review-panel");
    expect(source).toContain("rh-original-address");
    expect(source).toContain("rh-payment-panel");
    expect(source).toContain("rh-card-form");
    expect(source).toContain("rh-payment-agreements");
  });

  it("requires explicit checkout confirmations instead of hard-coded completed state", () => {
    const source = readSource("../src/pages/CheckoutPage.vue");

    expect(source).toContain("customNoticeAccepted = ref(false)");
    expect(source).toContain("cardComplete = ref(false)");
    expect(source).toContain("termsAccepted = ref(false)");
    expect(source).toContain("customNoticeAccepted.value");
    expect(source).toContain("cardComplete.value");
    expect(source).toContain("termsAccepted.value");
    expect(source).not.toContain("customNoticeAccepted: true");
    expect(source).not.toContain("cardComplete: true");
    expect(source).not.toContain("termsAccepted: true");
  });

  it("keeps checkout error recovery and flow status wiring", () => {
    const source = readSource("../src/pages/CheckoutPage.vue");

    expect(source).toContain("checkoutStepLabelKeys");
    expect(source).toContain("getCheckoutErrorKey");
    expect(source).toContain("getCheckoutRecoveryAction");
    expect(source).toContain('"checkout.errors.loadUnavailable"');
    expect(source).toContain('"checkout.errors.orderUnavailable"');
    expect(source).toContain('"checkout.errors.noAddress"');
    expect(source).toContain("checkoutRecoveryAction");
    expect(source).not.toContain("error === t('checkout.errors.noAddress')");
    expect(source).not.toContain("Payment details are ready for secure order submission.");
    expect(source).not.toContain("I agree to the checkout terms and order review requirements.");
    expect(source).not.toContain("Checkout service is unavailable. Please try again later.");
  });

  it("gates payment behind address review confirmation", () => {
    const source = readSource("../src/pages/CheckoutPage.vue");

    expect(source).toContain('checkoutStage = ref("shipping")');
    expect(source).toContain("addressReviewOpen = ref(false)");
    expect(source).toContain("continueWithOriginalAddress");
    expect(source).toContain('checkoutStage.value = "payment"');
    expect(source).toContain("const hasCheckoutAddress = computed");
    expect(source).toContain("const canReviewPayment = computed");
    expect(source).toContain("const hasShippingFormAddress = computed");
    expect(source).toContain("const canStartAddressReview = computed");
    expect(source).toContain("primaryActionDisabled");
    expect(source).toContain('checkoutStage.value === "shipping" && !canStartAddressReview.value');
    expect(source).toContain('checkoutStage.value === "payment" && !canSubmitPayment.value');
    expect(source).not.toContain('mode.value === "yudao" && !canPlaceCheckoutOrder(checkoutFlow.value)');
    expect(source).toContain(":disabled=\"primaryActionDisabled\"");
    expect(source).toContain("return null;");
    expect(source).toContain('v-if="checkoutStage === \'payment\'" class="rh-payment-panel"');
    expect(source).not.toContain('line1: "12 Main"');
    expect(source).not.toContain('city: "Boston"');
  });

  it("separates buyer address confirmation from choosing a suggested address", () => {
    const source = readSource("../src/pages/CheckoutPage.vue");

    expect(source).toContain("addressConfirmed: Boolean(addressConfirmationRecord.value)");
    expect(source).toContain('useSuggestedAddress.value = choice === "suggested"');
    expect(source).not.toContain("useSuggestedAddress.value = true;");
  });

  it("does not enter address review when checkout mode cannot create a Yudao order", () => {
    const source = readSource("../src/pages/CheckoutPage.vue");

    expect(source).toContain('if (mode.value === "token-required")');
    expect(source).toContain('errorKey.value = "checkout.errors.sessionExpired"');
    expect(source).toContain('if (mode.value === "local-preview")');
    expect(source).toContain('errorKey.value = "checkout.errors.orderUnavailable"');
    expect(source).toContain('if (primaryActionDisabled.value && checkoutStage.value !== "payment")');
    expect(source).not.toContain(":disabled=\"busy || mode === 'empty' || (checkoutStage === 'payment' && !canSubmitPayment)\"");
  });

  it("does not create a remote order without an address confirmation record", () => {
    const source = readSource("../src/pages/CheckoutPage.vue");

    expect(source).toContain("if (!addressConfirmationRecord.value)");
    expect(source).toContain('"checkout.errors.addressConfirmationRequired"');
    expect(source).toContain('checkoutStage.value = "shipping"');
    expect(source).toContain("return;");
  });

  it("does not submit remote orders or payment if checkout items are not still remote cart rows", () => {
    const source = readSource("../src/pages/CheckoutPage.vue");
    const submitOrderBody = source.slice(
      source.indexOf("const submitOrder = async () => {"),
      source.indexOf("const handlePrimaryAction = async () => {"),
    );

    expect(submitOrderBody).toContain("if (!canUseYudaoCheckout(props.items))");
    expect(submitOrderBody).toContain('errorKey.value = "checkout.errors.orderUnavailable"');
    expect(submitOrderBody).toContain("if (!readYudaoToken())");
    expect(submitOrderBody).toContain('errorKey.value = "checkout.errors.sessionExpired"');
    expect(submitOrderBody.indexOf("if (!canUseYudaoCheckout(props.items))")).toBeLessThan(
      submitOrderBody.indexOf("const payload = buildYudaoOrderPayload"),
    );
  });

  it("does not create a remote order when the address confirmation audit is incomplete", () => {
    const source = readSource("../src/pages/CheckoutPage.vue");

    expect(source).toContain("const addressVerificationAudit = buildAddressVerificationAudit(addressConfirmationRecord.value);");
    expect(source).toContain("if (!addressVerificationAudit)");
    expect(source).toContain("addressConfirmationRecord.value = null;");
    expect(source).toContain('checkoutStage.value = "shipping"');
    expect(source).toContain('"checkout.errors.addressConfirmationRequired"');
  });

  it("returns buyers to address review when backend rejects a missing confirmation audit", () => {
    const source = readSource("../src/pages/CheckoutPage.vue");

    expect(source).toContain('if (errorKey.value === "checkout.errors.addressConfirmationRequired")');
    expect(source).toContain('checkoutStage.value = "shipping"');
  });

  it("lets buyers restart address review from an address confirmation recovery action", () => {
    const source = readSource("../src/pages/CheckoutPage.vue");

    expect(source).toContain('checkoutRecoveryAction.value.type === "address-review"');
    expect(source).toContain('errorKey.value = ""');
    expect(source).toContain('checkoutStage.value = "shipping"');
    expect(source).toContain("addressReviewOpen.value = false");
  });

  it("invalidates confirmed address audit when buyers edit shipping details", () => {
    const source = readSource("../src/pages/CheckoutPage.vue");

    expect(source).toContain("const resetAddressConfirmationAfterShippingEdit = () =>");
    expect(source).toContain("addressConfirmationRecord.value = null;");
    expect(source).toContain("addressVerificationResult.value = null;");
    expect(source).toContain("const editConfirmedAddress = () =>");
    expect(source).toContain('@click="editConfirmedAddress"');
    expect(source).toContain("watch(shippingForm");
    expect(source).toContain("resetAddressConfirmationAfterShippingEdit();");
  });

  it("does not submit a payment order unless payment details and agreements are complete", () => {
    const source = readSource("../src/pages/CheckoutPage.vue");

    expect(source).toContain('checkoutStage.value === "payment" && !canSubmitPayment.value');
    expect(source).toContain('"checkout.errors.paymentRequired"');
    expect(source).toContain("return;");
  });

  it("blocks payment submission until a Yudao payment channel is configured", () => {
    const source = readSource("../src/pages/CheckoutPage.vue");

    expect(source).toContain("paymentChannelConfigured");
    expect(source).toContain("normalizeYudaoPayChannelCode");
    expect(source).toContain("normalizeYudaoPayChannelCode(import.meta.env.VITE_YUDAO_PAY_CHANNEL_CODE)");
    expect(source).toContain("Boolean(paymentChannelCode)");
    expect(source).toContain("paymentChannelConfigured.value");
    expect(source).toContain('"checkout.errors.paymentChannelUnavailable"');
    expect(source).toContain('t("checkout.payment.channelUnavailable")');
    expect(source).toContain('v-if="paymentRequired && !paymentChannelConfigured"');
  });

  it("saves hand-entered shipping addresses before payment and remote order creation", () => {
    const source = readSource("../src/pages/CheckoutPage.vue");

    expect(source).toContain("createRemoteAddressVerificationProvider");
    expect(source).toContain("addressVerificationProvider");
    expect(source).toContain("createMemberAddress");
    expect(source).toContain("verifyUsCheckoutAddressWithProvider");
    expect(source).toContain("buildAddressConfirmationRecord");
    expect(source).toContain("addressSource: selectedAddressSource.value");
    expect(source).toContain("buildYudaoAddressPayload");
    expect(source).toContain("saveShippingAddress");
    expect(source).toContain("const savedAddress = await createMemberAddress(payload)");
    expect(source).toContain("savedAddressId = savedAddress?.id ?? savedAddress");
    expect(source).toContain("selectedAddressId.value = savedAddressId");
    expect(source).toContain("await refreshSettlement(savedAddressId)");
    expect(source).toContain("addressConfirmation: addressConfirmationRecord.value");
    expect(source).toContain("verifyUsCheckoutAddressWithProvider(shippingForm.value, addressVerificationProvider)");
    expect(source).toContain('v-model="shippingForm.state"');
    expect(source).not.toContain("getAreaTree");
  });

  it("does not shadow the selected address computed while saving checkout addresses", () => {
    const source = readSource("../src/pages/CheckoutPage.vue");

    expect(source).toContain("selectedAddress.value");
    expect(source).not.toContain("const selectedAddress = addressConfirmationRecord.value?.selectedAddress || shippingForm.value");
  });

  it("lets buyers choose a saved address as the checkout address source", () => {
    const source = readSource("../src/pages/CheckoutPage.vue");

    expect(source).toContain("updateMemberAddress");
    expect(source).toContain("savedAddressToShippingForm");
    expect(source).toContain("resetShippingForm");
    expect(source).toContain("applySelectedSavedAddress");
    expect(source).toContain("handleSavedAddressSelectionChange");
    expect(source).toContain("selectedAddressSource");
    expect(source).toContain('if (selectedAddressSource.value === "new")');
    expect(source).toContain("resetShippingForm();");
    expect(source).toContain("String(address.id) === String(selectedAddressId.value)");
    expect(source).toContain('v-if="addresses.length"');
    expect(source).toContain('v-model="selectedAddressId"');
    expect(source).toContain('@change="handleSavedAddressSelectionChange"');
    expect(source).toContain("await refreshSettlement(selectedAddressId.value)");
    expect(source).toContain('t("checkout.shipping.savedAddresses")');
    expect(source).toContain('t("checkout.shipping.enterNewAddress")');
    expect(source).toContain("await updateMemberAddress({ id: selectedAddressId.value, ...payload })");
    expect(source).toContain("syncSavedAddressAfterSave");
    expect(source).toContain("addresses.value = addresses.value.map");
    expect(source).toContain("String(address.id) === String(savedAddress.id)");
  });

  it("keeps the latest address verification audit on saved address snapshots", () => {
    const source = readSource("../src/pages/CheckoutPage.vue");

    expect(source).toContain("buildAddressVerificationAudit");
    expect(source).toContain("addressVerification: buildAddressVerificationAudit(addressConfirmationRecord.value)");
    expect(source).toContain("addressConfirmation: addressConfirmationRecord.value");
  });

  it("does not save an address or enter payment when the chosen address audit is incomplete", () => {
    const source = readSource("../src/pages/CheckoutPage.vue");

    expect(source).toContain("const chosenAddressAudit = buildAddressVerificationAudit(addressConfirmationRecord.value);");
    expect(source).toContain("if (!chosenAddressAudit)");
    expect(source).toContain("addressReviewBusy.value = false;");
    expect(source).toContain("addressConfirmationRecord.value = null;");
    expect(source).toContain('"checkout.errors.addressConfirmationRequired"');
    expect(source).toContain("return;");
  });

  it("shows saved address verification history without skipping the current checkout review", () => {
    const source = readSource("../src/pages/CheckoutPage.vue");
    const i18n = readSource("../src/i18n.js");
    const styles = readSource("../src/styles.css");

    expect(source).toContain("buildAddressBookVerificationSummary");
    expect(source).toContain("selectedSavedAddressVerificationSummary");
    expect(source).toContain("selectedAddress.value?.addressVerification");
    expect(source).toContain("rh-saved-address-verification");
    expect(source).toContain('t("checkout.shipping.savedAddressVerification")');
    expect(source).toContain('t("checkout.shipping.savedAddressVerificationRecheck")');
    expect(source).toContain("selectedSavedAddressVerificationSummary.statusLabelKey");
    expect(source).toContain("selectedSavedAddressVerificationSummary.providerWarningKey");
    expect(source).toContain("selectedSavedAddressVerificationSummary.warningKey");
    expect(source).toContain("addressConfirmationRecord.value = null;");
    expect(i18n).toContain("savedAddressVerification");
    expect(i18n).toContain("savedAddressVerificationRecheck");
    expect(styles).toContain(".rh-saved-address-verification");
  });

  it("warns buyers when checkout address verification is currently using backend fallback", () => {
    const source = readSource("../src/pages/CheckoutPage.vue");
    const i18n = readSource("../src/i18n.js");
    const styles = readSource("../src/styles.css");

    expect(source).toContain("addressVerificationProviderStatus = ref(null)");
    expect(source).toContain("loadAddressVerificationProviderStatus");
    expect(source).toContain("addressVerificationProvider?.getStatus");
    expect(source).toContain("addressVerificationProviderFallbackWarning");
    expect(source).toContain("addressVerificationProviderStatus.value?.fallbackActive");
    expect(source).toContain('class="wide rh-address-provider-status"');
    expect(source).toContain('t(addressVerificationProviderFallbackWarning)');
    expect(i18n).toContain("addressVerificationFallbackWarning");
    expect(styles).toContain(".rh-address-provider-status");
  });

  it("localizes the address review confirmation dialog", () => {
    const source = readSource("../src/pages/CheckoutPage.vue");

    expect(source).toContain('t("checkout.addressReview.titleVerified")');
    expect(source).toContain('t("checkout.addressReview.titleReview")');
    expect(source).toContain('t("checkout.addressReview.suggestedMessage")');
    expect(source).toContain('t("checkout.addressReview.unverifiedMessage")');
    expect(source).toContain('t("checkout.addressReview.confirmMessage")');
    expect(source).toContain('t("checkout.addressReview.verifiedLabel")');
    expect(source).toContain('t("checkout.addressReview.enteredLabel")');
    expect(source).toContain('t("checkout.addressReview.suggestedLabel")');
    expect(source).toContain('t("checkout.addressReview.useSuggested")');
    expect(source).toContain('t("checkout.addressReview.useVerified")');
    expect(source).toContain('t("checkout.addressReview.useEntered")');
    expect(source).toContain('t("checkout.addressReview.editOriginal")');
    expect(source).toContain('t("checkout.addressReview.confirmationNotice")');
    expect(source).toContain('class="rh-address-review-notice"');
    expect(source).toContain("addressReviewReasonLabelKey");
    expect(source).toContain('class="rh-address-review-meta"');
    expect(source).toContain('t("checkout.addressConfirmation.reason")');
    expect(source).toContain("t(addressReviewReasonLabelKey)");
    expect(source).toContain('addressVerificationResult?.providerStatus === "fallback"');
    expect(source).toContain('t("checkout.addressReview.providerFallbackWarning")');
    expect(source).toContain('addressVerificationResult?.source === "local-postal-region"');
    expect(source).toContain('t("checkout.addressReview.localPostalRegionWarning")');
    expect(source).toContain('addressVerificationResult?.source === "backend-address-verification"');
    expect(source).toContain('t("checkout.addressReview.localOnlyVerificationWarning")');
    expect(source).not.toContain("Confirm Your Verified Address");
    expect(source).not.toContain("Review Your Shipping Address");
    expect(source).not.toContain("Use Address As Entered");
  });

  it("shows the buyer-confirmed address summary before payment submission", () => {
    const source = readSource("../src/pages/CheckoutPage.vue");
    const i18n = readSource("../src/i18n.js");

    expect(source).toContain("buildCheckoutAddressConfirmationSummary");
    expect(source).toContain("addressConfirmationSummary");
    expect(source).toContain('class="rh-address-confirmation-summary"');
    expect(source).toContain('t("checkout.addressConfirmation.title")');
    expect(source).toContain('t(addressConfirmationSummary.statusLabelKey)');
    expect(source).toContain('t(addressConfirmationSummary.choiceLabelKey)');
    expect(source).toContain('t(addressConfirmationSummary.addressSourceLabelKey)');
    expect(source).toContain('t("checkout.addressConfirmation.providerStatus")');
    expect(source).toContain("addressConfirmationSummary.providerStatusLabelKey");
    expect(source).toContain("addressConfirmationSummary.selected");
    expect(source).toContain("addressConfirmationSummary.warningKey");
    expect(source).toContain("addressConfirmationSummary.providerWarningKey");
    expect(source).toContain("addressConfirmationSummary.sourceWarningKey");
    expect(i18n).toContain("addressConfirmation");
    expect(i18n).toContain("warning:");
    expect(i18n).toContain("providerFallbackWarning");
    expect(i18n).toContain("localPostalRegionWarning");
  });

  it("localizes the shipping address form labels and placeholders", () => {
    const source = readSource("../src/pages/CheckoutPage.vue");

    expect(source).toContain('t("checkout.shipping.title")');
    expect(source).toContain('t("checkout.shipping.firstName")');
    expect(source).toContain('t("checkout.shipping.lastName")');
    expect(source).toContain('t("checkout.shipping.country")');
    expect(source).toContain('t("checkout.shipping.street")');
    expect(source).toContain('t("checkout.shipping.apartment")');
    expect(source).toContain('t("checkout.shipping.city")');
    expect(source).toContain('t("checkout.shipping.state")');
    expect(source).toContain('t("checkout.shipping.postalCode")');
    expect(source).toContain('t("checkout.shipping.phone")');
    expect(source).not.toContain("<h2>Shipping Address</h2>");
    expect(source).not.toContain('placeholder="Street Address"');
    expect(source).not.toContain('aria-label="Postal Code"');
  });

  it("localizes checkout header, payment, summary, agreements, and footer copy", () => {
    const source = readSource("../src/pages/CheckoutPage.vue");

    expect(source).toContain('t("checkout.header.shipToUnitedStates")');
    expect(source).toContain('t("checkout.header.title")');
    expect(source).toContain('t("checkout.header.shipping")');
    expect(source).toContain('t("checkout.header.payment")');
    expect(source).toContain('t("checkout.header.confirmation")');
    expect(source).toContain('t("checkout.payment.title")');
    expect(source).toContain('t("checkout.payment.intro")');
    expect(source).toContain('t("checkout.payment.saveCard")');
    expect(source).toContain('t("checkout.payment.billingSameAsShipping")');
    expect(source).toContain('t("checkout.payment.billingAddress")');
    expect(source).toContain('t("checkout.payment.edit")');
    expect(source).toContain('t("checkout.payment.giftMessage")');
    expect(source).toContain('t("checkout.payment.orderDescription")');
    expect(source).toContain('t("checkout.payment.viewCart")');
    expect(source).toContain('t("checkout.summary.memberSavings")');
    expect(source).toContain('t("checkout.summary.subtotalWithMemberSavings")');
    expect(source).toContain('t("checkout.summary.membersProgram")');
    expect(source).toContain('t("checkout.summary.unlimitedDelivery")');
    expect(source).toContain('t("checkout.agreements.membersTerms")');
    expect(source).toContain('t("checkout.footer.privacy")');
    expect(source).toContain('t("checkout.footer.shippingDelivery")');
    expect(source).toContain('t("checkout.footer.returnsExchanges")');
    expect(source).toContain('t("checkout.footer.accessibility")');
    expect(source).toContain('t("checkout.footer.contact")');
    expect(source).toContain('t("checkout.footer.copyright")');
    expect(source).not.toContain("<h1>Checkout</h1>");
    expect(source).not.toContain(">Ship to United States <");
    expect(source).not.toContain(">Payment</h2>");
    expect(source).not.toContain("Select a payment method to use.");
    expect(source).not.toContain("Save this credit card to my account");
    expect(source).not.toContain("Billing address same as shipping");
    expect(source).not.toContain(">Billing Address</h2>");
    expect(source).not.toContain(">Gift Message <");
    expect(source).not.toContain(">Order Description <");
    expect(source).not.toContain(">View Cart <");
    expect(source).not.toContain(">Member Savings<");
    expect(source).not.toContain("I agree to the <u>RH Members Program Terms and Conditions</u>");
    expect(source).not.toContain('href="/privacy">Privacy<');
  });

  it("opens the created order when the order API returns either an id or an object", () => {
    const source = readSource("../src/pages/CheckoutPage.vue");

    expect(source).toContain("getCreatedOrderId");
    expect(source).toContain('emit("order-created", createdOrderId)');
  });

  it("submits a Yudao payment order when the created order exposes a pay order id", () => {
    const source = readSource("../src/pages/CheckoutPage.vue");

    expect(source).toContain("buildPaymentReturnUrl");
    expect(source).toContain("buildYudaoPaymentPayload");
    expect(source).toContain("getPayOrderId");
    expect(source).toContain("getPaymentRedirectTarget");
    expect(source).toContain("submitPaymentFormDisplay");
    expect(source).toContain("submitPayOrder");
    expect(source).toContain("paymentChannelCode");
    expect(source).toContain('"checkout.errors.paymentUnavailable"');
    expect(source).toContain("createdOrderPath");
    expect(source).toContain("orderDetail: createdOrderPath.value");
    expect(source).toContain("if (!createdOrderId)");
    expect(source).toContain('errorKey.value = "checkout.errors.orderUnavailable"');
    expect(source).toContain("createdOrderPath.value = getOrderDetailPath(createdOrderId);");
    expect(source).toContain("createdOrderPath.value = getOrderDetailPath(createdOrderId, payOrderId);");
    expect(source).toContain("buildPaymentReturnUrl(window.location.origin, createdOrderId, payOrderId)");
    expect(source).toContain("if (!payOrderId)");
    expect(source).toContain("paymentResult = await submitPayOrder(paymentPayload)");
    expect(source).toContain("submitPaymentFormDisplay(paymentResult, window.document)");
    expect(source).toContain("window.location.assign(paymentRedirectTarget)");
    expect(source).toContain("if (!paymentRedirectTarget)");
    expect(source).toContain('errorKey.value = "checkout.errors.paymentUnavailable"');
  });

  it("opens the created order without payment submission when no pay order id is returned", () => {
    const source = readSource("../src/pages/CheckoutPage.vue");

    expect(source).toContain("if (!payOrderId) {");
    expect(source).toContain('emit("order-created", createdOrderId);');
    expect(source).toContain("return;");
    expect(source).not.toContain('if (!payOrderId) {\n      errorKey.value = "checkout.errors.paymentUnavailable";');
  });

  it("does not require payment channel or card details when settlement has no amount due", () => {
    const source = readSource("../src/pages/CheckoutPage.vue");

    expect(source).toContain("const paymentRequired = computed");
    expect(source).toContain("Number(settlement.value?.payPrice ?? displayEstimatedTotal.value) > 0");
    expect(source).toContain("paymentRequired.value &&");
    expect(source).toContain('checkoutStage.value === "payment" && paymentRequired.value && !paymentChannelConfigured.value');
    expect(source).toContain('checkoutStage.value === "payment" && paymentRequired.value && !canSubmitPayment.value');
    expect(source).toContain('v-if="paymentRequired && !paymentChannelConfigured"');
  });

  it("keeps unsupported payment method choices disabled before payment confirmation", () => {
    const source = readSource("../src/pages/CheckoutPage.vue");

    expect(source).toContain('const paymentMethod = ref("card")');
    expect(source).toContain("const paymentMethodOptions =");
    expect(source).toContain('value: "gift-card"');
    expect(source).toContain("enabled: false");
    expect(source).toContain('value: "member-credit"');
    expect(source).toContain("paymentMethod.value");
    expect(source).toContain("selectedPaymentMethodEnabled");
    expect(source).toContain("selectPaymentMethod");
    expect(source).toContain("if (!option.enabled) return;");
    expect(source).toContain("canSubmitPayment = computed");
    expect(source).toContain("selectedPaymentMethodEnabled.value");
    expect(source).toContain('v-for="option in paymentMethodOptions"');
    expect(source).toContain(":disabled=\"!option.enabled\"");
    expect(source).toContain("@click=\"selectPaymentMethod(option)\"");
    expect(source).toContain("rh-payment-methods");
    expect(source).toContain("paymentForm");
    expect(source).not.toContain('paymentMethod: "card"');
  });

  it("styles the RH checkout address review and payment surfaces", () => {
    const source = readSource("../src/styles.css");

    expect(source).toContain(".rh-address-review-layer");
    expect(source).toContain(".rh-address-review-panel");
    expect(source).toContain(".rh-original-address");
    expect(source).toContain(".rh-payment-panel");
    expect(source).toContain(".rh-payment-methods");
    expect(source).toContain(".rh-card-form");
    expect(source).toContain(".rh-address-confirmation-summary");
  });
});
