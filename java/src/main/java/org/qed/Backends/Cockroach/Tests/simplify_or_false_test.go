package rulescript_test

import (
	"testing"
)

func TestSimplifyOrFalse(t *testing.T) { testSimplifyOrLiteral(t, "SimplifyOrFalse", false, false) }

func TestSQLSimplifyOrFalse(t *testing.T) {
	expectSQLRewrites(t, "SimplifyOrFalse", "SELECT x FROM u WHERE x > 0 OR FALSE")
}
