package rulescript_test

import (
	"testing"
)

func TestFoldNonNullIsNotNull(t *testing.T) { testFoldNonNullScalar(t, "FoldNonNullIsNotNull", true) }

func TestSQLFoldNonNullIsNotNull(t *testing.T) {
	expectSQLFires(t, "FoldNonNullIsNotNull", "SELECT x FROM (VALUES (1), (2)) AS v(x) WHERE x IS NOT NULL")
}
