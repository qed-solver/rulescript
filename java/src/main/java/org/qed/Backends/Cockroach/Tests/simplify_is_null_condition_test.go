package rulescript_test

import (
	"testing"
)

func TestSimplifyIsNullCondition(t *testing.T) {
	testRemoveNullCondition(t, "SimplifyIsNullCondition", false)
}

func TestSQLSimplifyIsNullCondition(t *testing.T) {
	expectSQLFires(t, "SimplifyIsNullCondition", "SELECT a,b FROM (SELECT a,b FROM (VALUES (1, 2), (3, 4)) AS v(a,b) WHERE b > 0) AS q WHERE a IS NULL")
}
