package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestFoldNonNullTupleIsTupleNull(t *testing.T) {
	rule := loadRule(t, "FoldNonNullTupleIsTupleNull")
	e := newEnv(t)
	s, cols := e.scan("t", 2)
	nullA := e.f.ConstructIs(e.v(cols[0]), memo.NullSingleton)
	nullB := e.f.ConstructIs(e.v(cols[1]), memo.NullSingleton)
	want := e.f.ConstructSelect(s, e.filters(e.f.ConstructFalse()))
	e.expectRewrite(rule, e.f.ConstructSelect(s, e.filters(nullA, nullB)), want)
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(nullB, nullA)))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(nullA)))
}

func TestSQLFoldNonNullTupleIsTupleNull(t *testing.T) {
	expectSQLFires(t, "FoldNonNullTupleIsTupleNull", "SELECT a,b FROM (VALUES (1, NULL), (2, 3)) AS v(a,b) WHERE a IS NULL AND b IS NULL")
}
