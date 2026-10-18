package rulescript

import (
	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

type guard struct {
	fired    map[*Rule]map[memo.RelExpr]bool
	produced map[*Rule]map[memo.RelExpr]bool
}

func newGuard() *guard {
	return &guard{
		fired:    make(map[*Rule]map[memo.RelExpr]bool),
		produced: make(map[*Rule]map[memo.RelExpr]bool),
	}
}

func (g *guard) skip(r *Rule, e memo.RelExpr) bool { return g.fired[r][e] || g.produced[r][e] }

func (g *guard) record(r *Rule, e, out memo.RelExpr) {
	if g.fired[r] == nil {
		g.fired[r] = make(map[memo.RelExpr]bool)
		g.produced[r] = make(map[memo.RelExpr]bool)
	}
	g.fired[r][e] = true
	existing := make(map[memo.RelExpr]bool)
	relationalNodes(e, existing)
	created := make(map[memo.RelExpr]bool)
	relationalNodes(out, created)
	for node := range created {
		if !existing[node] { g.produced[r][node] = true }
	}
}

func relationalNodes(e opt.Expr, out map[memo.RelExpr]bool) {
	if rel, ok := e.(memo.RelExpr); ok {
		if out[rel] { return }
		out[rel] = true
	}
	for i, n := 0, e.ChildCount(); i < n; i++ { relationalNodes(e.Child(i), out) }
}
