package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestNormalizeSelectAnyFilter(t *testing.T) {
	rule := loadRule(t, "NormalizeSelectAnyFilter")
	e := newEnv(t)
	input, ic := e.scan("u", 1)
	sub, sc := e.scan("t", 1)
	membership := e.f.ConstructAny(sub, e.v(ic[0]), &memo.SubqueryPrivate{Cmp: opt.EqOp})
	before := e.f.ConstructSelect(input, e.filters(membership))
	correlated := e.f.ConstructSelect(sub, e.filters(e.f.ConstructEq(e.v(ic[0]), e.v(sc[0]))))
	want := e.f.ConstructSelect(input, e.filters(e.exists(correlated)))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(input, e.filters(e.f.ConstructNot(membership))))
	e.expectNoMatch(rule, e.f.ConstructSelect(input, e.filters(e.f.ConstructGt(e.v(ic[0]), e.num(0)))))
}

func TestSQLNormalizeSelectAnyFilter(t *testing.T) {
	expectSQLFires(t, "NormalizeSelectAnyFilter", "SELECT x FROM u WHERE x IN (SELECT a FROM t)")
}
