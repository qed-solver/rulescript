package rulescript_test

import (
	"testing"
)

func TestFoldIsNotNull(t *testing.T) { testFoldIsNullLiteral(t, "FoldIsNotNull", true) }

func TestSQLFoldIsNotNull(t *testing.T) {
	expectSQLFires(t, "FoldIsNotNull", "SELECT x FROM u WHERE NULL IS NOT NULL")
}
