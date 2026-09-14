# TRIPEER CMS permissions

V050 created a homepage pilot role with only page editing and site lookup. V051 expands
the existing TRIPEER `brand_operator` role and its tenant package to all seven existing
CMS modules: page content, site settings, navigation, articles, SEO metadata, keyword
analysis, and statistics/advertising code. The last module includes its separate code
update and publish permissions as part of the requested full CMS operator scope.

The migration resolves the tenant by `TRIPEER` and the role by `brand_operator`, verifies
that the package is not shared with another tenant, and includes menu parents and action
permissions. It retains existing grants and does not create users, change passwords,
reset site URLs, alter published content, or grant system management access.

The business-mode navigation catalog must include `/seo/page-content` when the user's
grants include `seo:page:query`. Deploy the current backend as well as the migration;
an older backend can hide that page even when its database grant exists. Re-login after
deployment to refresh the user's cached permission response and routes.

Validation covers all seven navigation paths, package membership of role grants, no
account seeding, and no non-CMS grants. Before test CD, exercise the migration on a
restricted database clone, compare unrelated tenants/accounts/content, restore the
backup, verify Flyway using the selected CI image, and register the matching deployment
compatibility review. Do not edit applied V050 or manually bypass the CD migration gate.

This enables the ERP management interfaces. The TRIPEER public website currently consumes
homepage hero content only; enabling navigation, articles, SEO, and tracking scripts on
that public website requires their corresponding frontend integrations.

Test ERP: `http://124.220.2.69/admin/`. A user created in another ERP environment is not
copied here by deployment. Use the administrator to select TRIPEER, create a user if
needed, and assign the existing brand operator role.
