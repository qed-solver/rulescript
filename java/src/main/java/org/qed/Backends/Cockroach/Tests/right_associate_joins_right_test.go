package rulescript_test

import (
	"testing"
)

func TestRightAssociateJoinsRight(t *testing.T) { testAssociateJoins(t, "RightAssociateJoinsRight") }

func TestSQLRightAssociateJoinsRight(t *testing.T) {
	expectSQLFires(t, "RightAssociateJoinsRight", "SELECT * FROM (VALUES (1)) AS a(x) JOIN ((VALUES (1)) AS b(y) CROSS JOIN (VALUES (1)) AS c(z)) ON a.x=c.z AND a.x=b.y")
}
