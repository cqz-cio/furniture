import { hasMembershipService } from "./membershipCart.js";

export const membershipRoutes = {
  membership: "/membership",
  membershipEnrollment: "/membership/enrollment",
  membershipTerms: "/membership/terms",
  membershipFaqs: "/membership/faqs",
  account: "/account",
  accountMembership: "/account/membership",
  accountOrders: "/account/orders",
  accountAddressBook: "/account/address-book",
  accountBilling: "/account/billing",
  accountPaymentMethods: "/account",
  accountWishlist: "/account",
  accountProfile: "/account/profile",
  accountGiftRegistry: "/gift-registry",
  checkoutAuth: "/checkout/auth",
  giftRegistry: "/gift-registry",
  giftRegistryCreate: "/gift-registry/create",
  giftRegistryFind: "/gift-registry/find",
  giftRegistryManage: "/gift-registry/manage",
};

export const accountMenuItems = [
  { label: "Account Profile", href: membershipRoutes.accountProfile },
  { label: "Address Book", href: membershipRoutes.accountAddressBook },
  { label: "Membership", href: membershipRoutes.accountMembership },
  { label: "Order History", href: membershipRoutes.accountOrders },
  { label: "Billing History", href: membershipRoutes.accountBilling },
  { label: "Payment Methods", href: membershipRoutes.accountPaymentMethods },
  { label: "Wish List", href: membershipRoutes.accountWishlist },
  { label: "Gift Registry", href: membershipRoutes.accountGiftRegistry },
];

export const accountMenuLabelKeys = {
  Membership: "membership.account.menuMembership",
  "Payment Methods": "membership.account.menuPaymentMethods",
  "Order History": "membership.account.menuOrderHistory",
  "Billing History": "membership.account.menuBillingHistory",
  "Wish List": "membership.account.menuWishlist",
  "Address Book": "membership.account.menuAddressBook",
  "Account Profile": "membership.account.menuProfile",
  "Gift Registry": "membership.account.menuGiftRegistry",
};

export const checkoutAuthOptions = [
  {
    key: "sign-in",
    title: "Sign In",
    description: "Access membership details, order information and saved addresses.",
    cta: "SIGN IN",
    href: "/account?return=/checkout",
    disabledForMembership: false,
  },
  {
    key: "create-account",
    title: "Create an Account",
    description: "Create an account to manage membership benefits, order history and delivery details.",
    cta: "CREATE AN ACCOUNT",
    href: "/account?mode=create&return=/checkout",
    disabledForMembership: false,
  },
  {
    key: "guest",
    title: "Continue as Guest",
    description: "Guest orders and delivery details may not be available in My Account later.",
    cta: "CONTINUE AS GUEST",
    href: "/checkout?guest=true",
    disabledForMembership: true,
  },
];

export const getCheckoutEntryRoute = (items = []) =>
  hasMembershipService(items) ? `${membershipRoutes.checkoutAuth}?intent=membership` : membershipRoutes.checkoutAuth;

export const getCheckoutAuthOptions = (items = []) => {
  const containsMembership = hasMembershipService(items);

  return checkoutAuthOptions.map((option) => {
    if (option.key !== "guest") return { ...option, disabled: false, reason: "" };

    return {
      ...option,
      disabled: containsMembership,
      reason: containsMembership ? "Guest checkout is not available when the bag contains a membership service." : "",
    };
  });
};

export const getMembershipJoinTarget = ({ signedIn, memberStatus }) => {
  if (!signedIn) return `${membershipRoutes.checkoutAuth}?intent=membership`;
  if (memberStatus === "active") return membershipRoutes.accountMembership;
  return membershipRoutes.membershipEnrollment;
};
