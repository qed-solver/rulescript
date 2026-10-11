package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFilterSetOpTranspose(t *testing.T) {
	rule := loadRule(t, "FilterSetOpTranspose")
	e := newEnv(t)
	l, lc := e.scan("t", 1)
	r, rc := e.scan("u", 1)
	out := e.f.Metadata().AddColumn("out", types.Int)
	private := &memo.SetPrivate{
		LeftCols:  opt.ColList{lc[0]},
		RightCols: opt.ColList{rc[0]},
		OutCols:   opt.ColList{out},
	}
	unionAll := e.f.ConstructUnionAll(l, r, private)
	before := e.f.ConstructSelect(unionAll, e.filters(e.f.ConstructGt(e.v(out), e.num(0))))
	lFiltered := e.f.ConstructSelect(l, e.filters(e.f.ConstructGt(e.v(lc[0]), e.num(0))))
	rFiltered := e.f.ConstructSelect(r, e.filters(e.f.ConstructGt(e.v(rc[0]), e.num(0))))
	want := e.f.ConstructUnionAll(lFiltered, rFiltered, private)
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructSelect(e.f.ConstructUnion(l, r, private),
		e.filters(e.f.ConstructGt(e.v(out), e.num(0)))))
	e.expectNoMatch(rule, e.f.ConstructSelect(e.f.ConstructIntersect(l, r, private),
		e.filters(e.f.ConstructGt(e.v(out), e.num(0)))))
	e.expectNoMatch(rule, e.f.ConstructSelect(l, e.filters(e.f.ConstructGt(e.v(lc[0]), e.num(0)))))
	e.expectNoMatch(rule, want)
}
