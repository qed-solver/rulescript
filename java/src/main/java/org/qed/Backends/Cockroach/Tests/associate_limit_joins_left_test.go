package rulescript_test

import (
	"testing"
)

func TestAssociateLimitJoinsLeft(t *testing.T) { testAssociateJoins(t, "AssociateLimitJoinsLeft") }

func TestSQLAssociateLimitJoinsLeft(t *testing.T) {
	expectSQLFires(t, "AssociateLimitJoinsLeft", "SELECT * FROM ((VALUES (1)) AS a(x) LEFT JOIN (VALUES (1)) AS b(y) ON a.x=b.y) JOIN (VALUES (1)) AS c(z) ON a.x=c.z")
}
