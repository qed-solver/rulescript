package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFoldNullBinaryLeft(t *testing.T) {
	rule := loadRule(t, "FoldNullBinaryLeft")
	e := newEnv(t)
	s, cols := e.scan("u", 1)
	null := e.f.ConstructNull(types.Int)
	before := e.f.ConstructSelect(s, e.filters(e.f.ConstructLt(null, e.v(cols[0]))))
	want := e.f.ConstructSelect(s, e.filters(e.f.ConstructNull(types.Bool)))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructLt(e.v(cols[0]), e.num(1)))))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructGt(null, e.v(cols[0])))))
}

func TestSQLFoldNullBinaryLeft(t *testing.T) {
	expectSQLFires(t, "FoldNullBinaryLeft", "SELECT x FROM u WHERE CAST(NULL AS INT) < x")
}
