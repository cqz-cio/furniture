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
  accountPaymentMethods: "/account/payment-methods",
  accountWishlist: "/account/wishlist",
  accountProfile: "/account/profile",
  accountGiftRegistry: "/account/gift-registry",
  checkoutAuth: "/checkout/auth",
  giftRegistry: "/gift-registry",
  giftRegistryCreate: "/gift-registry/create",
  giftRegistryManage: "/gift-registry/manage",
};

export const accountMenuItems = [
  { label: "Membership", href: membershipRoutes.accountMembership },
  { label: "Payment Methods", href: membershipRoutes.accountPaymentMethods },
  { label: "Order History", href: membershipRoutes.accountOrders },
  { label: "Wish List", href: membershipRoutes.accountWishlist },
  { label: "Address Book", href: membershipRoutes.accountAddressBook },
  { label: "Account Profile", href: membershipRoutes.accountProfile },
  { label: "Gift Registry", href: membershipRoutes.accountGiftRegistry },
];

export const checkoutAuthOptions = [
  {
    key: "sign-in",
    title: "Sign In",
    description: "Access membership details, order information and saved addresses.",
    cta: "SIGN IN",
    href: "/account/sign-in?return=/checkout",
    disabledForMembership: false,
  },
  {
    key: "create-account",
    title: "Create an Account",
    description: "Create an account to manage membership benefits, order history and delivery details.",
    cta: "CREATE AN ACCOUNT",
    href: "/account/register?return=/checkout",
    disabledForMembership: false,
  },
  {
    key: "guest",
    title: "Continue as Guest",
    description: "Guest orders and delivery details may not be available in My Account later.",
    cta: "CONTINUE AS GUEST",
    href: "/checkout/shipping?guest=true",
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
