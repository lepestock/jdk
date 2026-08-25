# Mainline identity-class profile.
# Keep generated sources compatible with mainline javac/java by disabling value classes.
--identity-value-class-balance=0

# Keep plain generation responsive enough for smoke and pool preselection runs.
--classes-limit=6
--member-functions-limit=8
--data-member-limit=6
--statement-limit=10
--test-statement-limit=50
--operator-limit=35
