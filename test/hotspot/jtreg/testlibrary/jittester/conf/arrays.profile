# JITTester array-focused profile
# Use with: --profile-file conf/arrays.profile

--arrays-disable=false
# FIXME: non-int collection element types currently expose invalid casted-lvalue
# shapes in array kernels; remove once typed collection lvalues are reworked.
--arrays-allowed-types int
--array-production-weight-bonus=1500
--arrays-field-definition-weight-bonus=1500
--array-kernel-body-complexity-percent=130
--array-kernel-body-statement-percent=150
--collection-print-reduction-percent=90
