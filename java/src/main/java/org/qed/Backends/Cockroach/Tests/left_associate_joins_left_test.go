package rulescript_test

import (
	"testing"
)

func TestLeftAssociateJoinsLeft(t *testing.T) { testAssociateJoins(t, "LeftAssociateJoinsLeft") }

func TestSQLLeftAssociateJoinsLeft(t *testing.T) {
	expectSQLFires(t, "LeftAssociateJoinsLeft", "SELECT * FROM ((VALUES (1)) AS a(x) CROSS JOIN (VALUES (1)) AS b(y)) JOIN (VALUES (1)) AS c(z) ON a.x=c.z AND b.y=c.z")
}
