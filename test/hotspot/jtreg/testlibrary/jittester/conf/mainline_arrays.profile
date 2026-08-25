# Mainline array-focused profile.
# Keep generated sources compatible with mainline javac/java by disabling value classes.
--identity-value-class-balance=0

# Keep plain generation responsive enough for smoke and pool preselection runs.
--classes-limit=6
--member-functions-limit=8
--data-member-limit=6
--statement-limit=18
--test-statement-limit=120
--operator-limit=35

--arrays-disable=false
# FIXME: non-int collection element types currently expose invalid casted-lvalue
# shapes in array kernels; remove once typed collection lvalues are reworked.
--arrays-allowed-types int
--array-production-weight-bonus=1500
--arrays-field-definition-weight-bonus=1500
--array-kernel-body-statement-percent=150
--collection-print-reduction-percent=90
