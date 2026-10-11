package rulescript_test

import (
	"testing"
)

func TestRemoveNotNullCondition(t *testing.T) {
	testRemoveNullCondition(t, "RemoveNotNullCondition", true)
}

func TestSQLRemoveNotNullCondition(t *testing.T) {
	expectSQLFires(t, "RemoveNotNullCondition", "SELECT a,b FROM (SELECT a,b FROM (VALUES (1, 2), (3, 4)) AS v(a,b) WHERE b > 0) AS q WHERE a IS NOT NULL")
}
