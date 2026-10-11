package rulescript_test

import (
	"testing"
)

func TestRightAssociateJoinsLeft(t *testing.T) { testAssociateJoins(t, "RightAssociateJoinsLeft") }

func TestSQLRightAssociateJoinsLeft(t *testing.T) {
	expectSQLFires(t, "RightAssociateJoinsLeft", "SELECT * FROM (VALUES (1)) AS a(x) JOIN ((VALUES (1)) AS b(y) CROSS JOIN (VALUES (1)) AS c(z)) ON a.x=b.y AND b.y=c.z")
}
