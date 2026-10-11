package rulescript_test

import (
	"testing"
)

func TestSimplifyTrueAnd(t *testing.T) { testAndTrue(t, "SimplifyTrueAnd", true) }

func TestSQLSimplifyTrueAnd(t *testing.T) {
	expectSQLFires(t, "SimplifyTrueAnd", "SELECT x FROM u WHERE TRUE AND x > 0")
}
