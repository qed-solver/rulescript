package rulescript_test

import (
	"testing"
)

func TestAssociateLimitJoinsRight(t *testing.T) { testAssociateJoins(t, "AssociateLimitJoinsRight") }

func TestSQLAssociateLimitJoinsRight(t *testing.T) {
	expectSQLFires(t, "AssociateLimitJoinsRight", "SELECT * FROM (VALUES (1)) AS c(z) JOIN ((VALUES (1)) AS a(x) LEFT JOIN (VALUES (1)) AS b(y) ON a.x=b.y) ON a.x=c.z")
}
