package rulescript_test

import (
	"testing"
)

func TestSimplifyRange(t *testing.T) { testAndTrue(t, "SimplifyRange", false) }

func TestSQLSimplifyRange(t *testing.T) {
	expectSQLFires(t, "SimplifyRange", "SELECT x FROM u WHERE x > 0 AND TRUE")
}
