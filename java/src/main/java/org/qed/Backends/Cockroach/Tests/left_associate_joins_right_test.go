package rulescript_test

import (
	"testing"
)

func TestLeftAssociateJoinsRight(t *testing.T) { testAssociateJoins(t, "LeftAssociateJoinsRight") }

func TestSQLLeftAssociateJoinsRight(t *testing.T) {
	expectSQLFires(t, "LeftAssociateJoinsRight", "SELECT * FROM ((VALUES (1)) AS a(x) CROSS JOIN (VALUES (1)) AS b(y)) JOIN (VALUES (1)) AS c(z) ON a.x=c.z AND b.y=c.z")
}
