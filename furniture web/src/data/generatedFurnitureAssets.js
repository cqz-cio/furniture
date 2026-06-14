const asset = (name) => `/assets/generated-furniture/${name}`;

export const generatedFurnitureAssets = {
  home: {
    hero: {
      desktop: asset("home-hero-desktop.webp"),
      mobile: asset("home-hero-mobile.webp"),
    },
    modules: {
      "002": {
        desktop: asset("home-module-002-bedroom-desktop.webp"),
        mobile: asset("home-module-002-bedroom-mobile.webp"),
      },
      "003": {
        desktop: asset("home-module-003-dining-desktop.webp"),
        mobile: asset("home-module-003-dining-mobile.webp"),
      },
      "004": {
        desktop: asset("home-module-004-outdoor-desktop.webp"),
        mobile: asset("home-module-004-outdoor-mobile.webp"),
      },
      "005": {
        desktop: asset("home-module-005-sourcebook-desktop.webp"),
        mobile: asset("home-module-005-sourcebook-mobile.webp"),
      },
    },
  },
  sale: {
    hero: {
      desktop: asset("sale-hero-desktop.webp"),
      mobile: asset("sale-hero-mobile.webp"),
    },
    membership: {
      desktop: asset("sale-membership-desktop.webp"),
      mobile: asset("sale-membership-mobile.webp"),
    },
    categories: {
      Living: {
        desktop: asset("sale-category-living-desktop.webp"),
        mobile: asset("sale-category-living-mobile.webp"),
      },
      Sofas: {
        desktop: asset("sale-category-sofas-desktop.webp"),
        mobile: asset("sale-category-sofas-mobile.webp"),
      },
      Dining: {
        desktop: asset("sale-category-dining-desktop.webp"),
        mobile: asset("sale-category-dining-mobile.webp"),
      },
      Bedroom: {
        desktop: asset("sale-category-bedroom-desktop.webp"),
        mobile: asset("sale-category-bedroom-mobile.webp"),
      },
      Bath: {
        desktop: asset("sale-category-bath-desktop.webp"),
        mobile: asset("sale-category-bath-mobile.webp"),
      },
      Outdoor: {
        desktop: asset("sale-category-outdoor-desktop.webp"),
        mobile: asset("sale-category-outdoor-mobile.webp"),
      },
      Rugs: {
        desktop: asset("sale-category-rugs-desktop.webp"),
        mobile: asset("sale-category-rugs-mobile.webp"),
      },
      Lighting: {
        desktop: asset("sale-category-lighting-desktop.webp"),
        mobile: asset("sale-category-lighting-mobile.webp"),
      },
      Bedding: {
        desktop: asset("sale-category-bedding-desktop.webp"),
        mobile: asset("sale-category-bedding-mobile.webp"),
      },
      "Bath Towels": {
        desktop: asset("sale-category-bath-towels-desktop.webp"),
        mobile: asset("sale-category-bath-towels-mobile.webp"),
      },
    },
  },
  outdoor: {
    hero: {
      desktop: asset("outdoor-landing-hero-desktop.webp"),
      mobile: asset("outdoor-landing-hero-mobile.webp"),
    },
  },
  babyChild: {
    hero: {
      desktop: asset("baby-child-hero-desktop.webp"),
      mobile: asset("baby-child-hero-mobile.webp"),
    },
    collections: {
      nursery: asset("baby-child-nursery.webp"),
      playroom: asset("baby-child-playroom.webp"),
      study: asset("baby-child-study.webp"),
      lighting: asset("baby-child-lighting.webp"),
    },
  },
  teen: {
    hero: {
      desktop: asset("teen-hero-desktop.webp"),
      mobile: asset("teen-hero-mobile.webp"),
    },
    collections: {
      bedroom: asset("teen-bedroom.webp"),
      lounge: asset("teen-lounge.webp"),
      study: asset("teen-study.webp"),
      lighting: asset("teen-lighting.webp"),
    },
  },
  products: {
    sofa: {
      cover: asset("product-sofa-cover.webp"),
      gallery: asset("product-sofa-gallery.webp"),
    },
    bed: {
      cover: asset("product-bed-cover.webp"),
      gallery: asset("product-bed-gallery.webp"),
    },
    table: {
      cover: asset("product-table-cover.webp"),
      gallery: asset("product-table-gallery.webp"),
    },
    chair: {
      cover: asset("product-chair-cover.webp"),
      gallery: asset("product-chair-gallery.webp"),
    },
    pendant: {
      cover: asset("product-pendant-cover.webp"),
      gallery: asset("product-pendant-gallery.webp"),
    },
  },
};
