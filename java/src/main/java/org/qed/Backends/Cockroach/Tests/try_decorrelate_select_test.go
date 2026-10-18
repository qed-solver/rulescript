package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestTryDecorrelateSelect(t *testing.T) {
	rule := loadRule(t, "TryDecorrelateSelect")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	on := e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))
	pred := e.f.ConstructEq(e.v(rc[0]), e.num(7))
	before := e.f.ConstructInnerJoin(l, e.f.ConstructSelect(r, e.filters(pred)), e.filters(on), &memo.JoinPrivate{})
	want := e.f.ConstructInnerJoin(l, r, e.filters(on, pred), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	leftJoin := e.f.ConstructLeftJoin(l, e.f.ConstructSelect(r, e.filters(pred)), e.filters(on), &memo.JoinPrivate{})
	e.expectNoMatch(rule, leftJoin)

}
