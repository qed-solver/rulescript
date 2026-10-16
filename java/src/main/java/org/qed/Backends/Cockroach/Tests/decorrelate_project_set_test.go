package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestDecorrelateProjectSet(t *testing.T) {
	rule := loadRule(t, "DecorrelateProjectSet")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	emitted := e.f.ConstructGt(e.v(rc[0]), e.num(0))
	before := e.f.ConstructInnerJoin(l, r, e.filters(emitted), &memo.JoinPrivate{})
	want := e.f.ConstructInnerJoin(l, e.f.ConstructSelect(r, e.filters(emitted)), nil, &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, r,
		e.filters(e.f.ConstructGt(e.v(lc[0]), e.num(0))), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructLeftJoin(l, r, e.filters(emitted), &memo.JoinPrivate{}))
}
